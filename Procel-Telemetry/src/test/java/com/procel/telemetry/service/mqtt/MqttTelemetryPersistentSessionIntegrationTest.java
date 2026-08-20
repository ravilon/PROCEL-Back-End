package com.procel.telemetry.service.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.TestJwt;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.observability.TelemetryObservabilityMetrics;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import com.procel.telemetry.service.PayloadHashService;
import com.procel.telemetry.service.TelemetryIngestService;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "procel.telemetry.mqtt.enabled=true",
        "procel.telemetry.mqtt.client-id=procel-telemetry-persistent-test",
        "procel.telemetry.mqtt.clean-start=false",
        "procel.telemetry.mqtt.session-expiry=PT1H",
        "procel.telemetry.mqtt.automatic-reconnect=true",
        "procel.telemetry.mqtt.reconnect-min-delay=PT1S",
        "procel.telemetry.mqtt.reconnect-max-delay=PT2S"
})
@Testcontainers(disabledWithoutDocker = true)
class MqttTelemetryPersistentSessionIntegrationTest {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    static HiveMQContainer hivemq = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:2024.3"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("procel.security.jwt.secret", () -> TestJwt.SECRET);
        registry.add("procel.telemetry.mqtt.broker-url", MqttTelemetryPersistentSessionIntegrationTest::brokerUrl);
    }

    @Autowired RawTelemetryEventRepository repository;
    @Autowired MqttTelemetrySubscriber subscriber;
    @Autowired FaultInjectingTelemetryIngestService faultInjectingIngestService;

    private MqttAsyncClient publisher;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        publisher = new MqttAsyncClient(brokerUrl(), "publisher-" + UUID.randomUUID(), new MemoryPersistence());
        publisher.connect(options(true)).waitForCompletion();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (publisher != null && publisher.isConnected()) {
            publisher.disconnect().waitForCompletion();
        }
        if (publisher != null) {
            publisher.close();
        }
    }

    @Test
    void transientMongoFailureIsNotAcknowledgedAndRedeliversAfterReconnect() throws Exception {
        String topic = "procel/telemetry/v1/producer-redelivery/sensor-1/events";
        faultInjectingIngestService.failOnce("msg-redelivery");

        publish(topic, envelope("msg-redelivery", "sensor-1", 1));
        awaitAttempts("msg-redelivery", 1);
        assertThat(repository.count()).isZero();

        subscriber.stop();
        subscriber.start();

        awaitCount(1);
        RawTelemetryEvent event = repository.findAll().getFirst();
        assertThat(event.getProducerId()).isEqualTo("producer-redelivery");
        assertThat(faultInjectingIngestService.attempts("msg-redelivery")).isGreaterThanOrEqualTo(2);
    }

    private void publish(String topic, String payload) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        publisher.publish(topic, message).waitForCompletion();
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

    private void awaitAttempts(String messageId, int count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            if (faultInjectingIngestService.attempts(messageId) >= count) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(faultInjectingIngestService.attempts(messageId)).isGreaterThanOrEqualTo(count);
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

    @TestConfiguration
    static class MqttTelemetryPersistentSessionIntegrationTestConfig {
        @Bean
        @Primary
        FaultInjectingTelemetryIngestService faultInjectingTelemetryIngestService(
                RawTelemetryEventRepository repository,
                PayloadHashService payloadHashService,
                TelemetryProperties properties,
                ObjectMapper objectMapper,
                TelemetryObservabilityMetrics metrics
        ) {
            return new FaultInjectingTelemetryIngestService(repository, payloadHashService, properties, objectMapper, metrics);
        }
    }

    static class FaultInjectingTelemetryIngestService extends TelemetryIngestService {
        private final ConcurrentMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        FaultInjectingTelemetryIngestService(
                RawTelemetryEventRepository repository,
                PayloadHashService payloadHashService,
                TelemetryProperties properties,
                ObjectMapper objectMapper,
                TelemetryObservabilityMetrics metrics
        ) {
            super(repository, payloadHashService, properties, objectMapper, metrics);
        }

        void failOnce(String messageId) {
            failures.put(messageId, new AtomicInteger(1));
        }

        int attempts(String messageId) {
            AtomicInteger value = attempts.get(messageId);
            return value == null ? 0 : value.get();
        }

        @Override
        public TelemetryEventDTOs.IngestResponse ingest(String producerId, JsonNode request) {
            String messageId = request.get("messageId").asText();
            attempts.computeIfAbsent(messageId, ignored -> new AtomicInteger()).incrementAndGet();
            AtomicInteger remainingFailures = failures.get(messageId);
            if (remainingFailures != null && remainingFailures.getAndDecrement() > 0) {
                throw new DataAccessResourceFailureException("simulated MongoDB outage");
            }
            return super.ingest(producerId, request);
        }
    }
}
