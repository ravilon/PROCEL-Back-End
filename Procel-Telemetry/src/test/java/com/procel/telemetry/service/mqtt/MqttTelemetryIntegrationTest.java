package com.procel.telemetry.service.mqtt;

import com.procel.telemetry.TestJwt;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.TelemetrySource;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "procel.telemetry.mqtt.enabled=true",
        "procel.telemetry.mqtt.client-id=procel-telemetry-test",
        "procel.telemetry.mqtt.clean-start=true",
        "procel.telemetry.mqtt.session-expiry=PT1H",
        "procel.telemetry.mqtt.automatic-reconnect=true",
        "procel.telemetry.mqtt.reconnect-min-delay=PT1S",
        "procel.telemetry.mqtt.reconnect-max-delay=PT2S"
})
@Testcontainers(disabledWithoutDocker = true)
class MqttTelemetryIntegrationTest {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    static HiveMQContainer hivemq = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:2024.3"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("procel.security.jwt.secret", () -> TestJwt.SECRET);
        registry.add("procel.telemetry.mqtt.broker-url", MqttTelemetryIntegrationTest::brokerUrl);
    }

    @Autowired RawTelemetryEventRepository repository;
    @Autowired MqttTelemetrySubscriber subscriber;

    private MqttAsyncClient publisher;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        publisher = client("publisher-" + UUID.randomUUID(), true);
        publisher.connect(options(true)).waitForCompletion();
    }

    @AfterEach
    void tearDown() throws Exception {
        clearRetained("procel/telemetry/v1/producer-retained/sensor-1/events");
        if (publisher != null && publisher.isConnected()) {
            publisher.disconnect().waitForCompletion();
        }
        if (publisher != null) {
            publisher.close();
        }
    }

    @Test
    void mqttPublicationPersistsRawTelemetryEvent() throws Exception {
        publish("procel/telemetry/v1/producer-a/sensor-1/events", envelope("msg-pub", "sensor-1", 1), false);

        RawTelemetryEvent event = awaitOne();

        assertThat(event.getProducerId()).isEqualTo("producer-a");
        assertThat(event.getSource()).isEqualTo(TelemetrySource.MQTT);
        assertThat(event.getMessageId()).isEqualTo("msg-pub");
        assertThat(event.getSensorId()).isEqualTo("sensor-1");
    }

    @Test
    void equivalentDuplicateIsAcknowledgedAndNotPersistedAgain() throws Exception {
        String topic = "procel/telemetry/v1/producer-dup/sensor-1/events";
        String payload = envelope("msg-dup", "sensor-1", 1);

        publish(topic, payload, false);
        publish(topic, payload, false);

        awaitCount(1);
        assertThat(repository.findAll().getFirst().getMessageId()).isEqualTo("msg-dup");
    }

    @Test
    void idempotencyConflictIsAcknowledgedAndNotPersistedAgain() throws Exception {
        String topic = "procel/telemetry/v1/producer-conflict/sensor-1/events";

        publish(topic, envelope("msg-conflict", "sensor-1", 1), false);
        publish(topic, envelope("msg-conflict", "sensor-1", 2), false);

        awaitCount(1);
        assertThat(repository.findAll().getFirst().getMessageId()).isEqualTo("msg-conflict");
    }

    @Test
    void retainedMessagesAreRejectedByDefault() throws Exception {
        subscriber.stop();
        publish("procel/telemetry/v1/producer-retained/sensor-1/events", envelope("msg-retained", "sensor-1", 1), true);
        subscriber.start();

        Thread.sleep(500);

        assertThat(repository.count()).isZero();
    }

    @Test
    void oversizedPayloadIsRejectedBeforeJsonParsing() throws Exception {
        publish("procel/telemetry/v1/producer-large/sensor-1/events", "x".repeat(262145), false);

        Thread.sleep(500);

        assertThat(repository.count()).isZero();
    }

    @Test
    void topicWithoutSensorUsesEnvelopeSensorAndDivergentSensorIsRejected() throws Exception {
        publish("procel/telemetry/v1/producer-nosensor/events", envelope("msg-ok", "sensor-body", 1), false);
        publish("procel/telemetry/v1/producer-nosensor/sensor-topic/events", envelope("msg-bad", "sensor-body", 1), false);

        awaitCount(1);
        RawTelemetryEvent event = repository.findAll().getFirst();
        assertThat(event.getMessageId()).isEqualTo("msg-ok");
        assertThat(event.getSensorId()).isEqualTo("sensor-body");
    }

    @Test
    void subscriberCanRestartAndResubscribe() throws Exception {
        subscriber.stop();
        subscriber.start();

        publish("procel/telemetry/v1/producer-reconnect/sensor-1/events", envelope("msg-reconnect", "sensor-1", 1), false);

        awaitCount(1);
        assertThat(repository.findAll().getFirst().getMessageId()).isEqualTo("msg-reconnect");
    }

    @Test
    void manualAckRedeliversUnacknowledgedQosOneMessage() throws Exception {
        String topic = "procel/acktest/v1/events";
        String clientId = "manual-ack-" + UUID.randomUUID();
        CountDownLatch firstDelivery = new CountDownLatch(1);
        CountDownLatch secondDelivery = new CountDownLatch(1);
        int[] deliveries = {0};

        MqttAsyncClient subscriberClient = client(clientId, false);
        subscriberClient.setManualAcks(true);
        subscriberClient.setCallback(new MqttCallback() {
            @Override
            public void disconnected(org.eclipse.paho.mqttv5.client.MqttDisconnectResponse disconnectResponse) {}

            @Override
            public void mqttErrorOccurred(MqttException exception) {}

            @Override
            public void messageArrived(String arrivedTopic, MqttMessage message) throws Exception {
                deliveries[0]++;
                if (deliveries[0] == 1) {
                    firstDelivery.countDown();
                    return;
                }
                subscriberClient.messageArrivedComplete(message.getId(), message.getQos());
                secondDelivery.countDown();
            }

            @Override
            public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken token) {}

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {}

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {}
        });

        subscriberClient.connect(options(false)).waitForCompletion();
        subscriberClient.subscribe(topic, 1).waitForCompletion();
        publish(topic, "{\"messageId\":\"ack\"}", false);
        assertThat(firstDelivery.await(5, TimeUnit.SECONDS)).isTrue();

        subscriberClient.disconnectForcibly();
        subscriberClient.connect(options(false)).waitForCompletion();
        subscriberClient.subscribe(topic, 1).waitForCompletion();

        assertThat(secondDelivery.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(deliveries[0]).isEqualTo(2);
        subscriberClient.disconnect().waitForCompletion();
        subscriberClient.close();
    }

    private RawTelemetryEvent awaitOne() throws InterruptedException {
        awaitCount(1);
        return repository.findAll().getFirst();
    }

    private void awaitCount(long count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            if (repository.count() == count) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(repository.count()).isEqualTo(count);
    }

    private void publish(String topic, String payload, boolean retained) throws Exception {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(retained);
        publisher.publish(topic, message).waitForCompletion();
    }

    private void clearRetained(String topic) throws Exception {
        if (publisher == null || !publisher.isConnected()) return;
        MqttMessage message = new MqttMessage(new byte[0]);
        message.setQos(1);
        message.setRetained(true);
        publisher.publish(topic, message).waitForCompletion();
    }

    private static MqttAsyncClient client(String clientId, boolean cleanStart) throws MqttException {
        return new MqttAsyncClient(brokerUrl(), clientId, new MemoryPersistence());
    }

    private static MqttConnectionOptions options(boolean cleanStart) {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(cleanStart);
        options.setSessionExpiryInterval(3600L);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(10);
        return options;
    }

    private static String brokerUrl() {
        return "tcp://" + hivemq.getHost() + ":" + hivemq.getMqttPort();
    }

    private static String envelope(String messageId, String sensorId, int value) {
        return """
                {"messageId":"%s","sensorId":"%s","sourceTimestamp":"2026-08-19T12:00:00Z","payload":{"value":%d}}
                """.formatted(messageId, sensorId, value);
    }
}
