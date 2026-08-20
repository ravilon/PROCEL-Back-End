package com.procel.telemetry.service.mqtt;

import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.observability.TelemetryObservabilityMetrics;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@ConditionalOnProperty(prefix = "procel.telemetry.mqtt", name = "enabled", havingValue = "true")
public class MqttTelemetrySubscriber implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);

    private final TelemetryProperties properties;
    private final MqttTelemetryClientFactory clientFactory;
    private final MqttTelemetryMessageHandler handler;
    private final TelemetryObservabilityMetrics metrics;
    private volatile MqttAsyncClient client;
    private volatile boolean running;

    public MqttTelemetrySubscriber(
            TelemetryProperties properties,
            MqttTelemetryClientFactory clientFactory,
            MqttTelemetryMessageHandler handler,
            TelemetryObservabilityMetrics metrics
    ) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.handler = handler;
        this.metrics = metrics;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        try {
            TelemetryProperties.Mqtt mqtt = properties.getMqtt();
            client = clientFactory.createClient(mqtt);
            client.setCallback(callback());
            client.connect(clientFactory.connectionOptions(mqtt)).waitForCompletion();
            subscribe();
            running = true;
            log.info("MQTT telemetry subscriber started: brokerUrl={}, clientId={}, qos={}, topicFilters={}",
                    sanitizedBrokerUrl(mqtt.getBrokerUrl()), mqtt.getClientId(), mqtt.getQos(), mqtt.getTopicFilters());
        } catch (MqttException ex) {
            running = false;
            throw new IllegalStateException("failed to start MQTT telemetry subscriber", ex);
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        MqttAsyncClient current = client;
        if (current == null) return;
        try {
            if (current.isConnected()) {
                current.disconnect().waitForCompletion();
            }
            current.close();
            log.info("MQTT telemetry subscriber stopped");
        } catch (MqttException ex) {
            log.warn("failed to stop MQTT telemetry subscriber cleanly", ex);
        } finally {
            client = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private MqttCallback callback() {
        return new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                running = false;
                log.warn("MQTT telemetry subscriber disconnected: reasonCode={}",
                        disconnectResponse != null ? disconnectResponse.getReturnCode() : null);
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {
                log.warn("MQTT telemetry subscriber error: reasonCode={}",
                        exception != null ? exception.getReasonCode() : null);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                MqttTelemetryMessageHandler.HandlingDecision decision = handler.handle(topic, message);
                if (decision == MqttTelemetryMessageHandler.HandlingDecision.ACK) {
                    client.messageArrivedComplete(message.getId(), message.getQos());
                }
            }

            @Override
            public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken token) {}

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                running = true;
                if (reconnect) {
                    try {
                        subscribe();
                        metrics.mqttReconnect();
                        log.info("application=procel-telemetry event=mqtt_reconnected status=success");
                    } catch (MqttException ex) {
                        log.warn("application=procel-telemetry event=mqtt_reconnected status=resubscribe_failed", ex);
                    }
                }
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {}
        };
    }

    private void subscribe() throws MqttException {
        TelemetryProperties.Mqtt mqtt = properties.getMqtt();
        String[] filters = mqtt.getTopicFilters().toArray(String[]::new);
        int[] qos = new int[filters.length];
        Arrays.fill(qos, mqtt.getQos());
        client.subscribe(filters, qos).waitForCompletion();
    }

    private static String sanitizedBrokerUrl(String brokerUrl) {
        if (brokerUrl == null) return null;
        int schemeSeparator = brokerUrl.indexOf("://");
        int at = brokerUrl.indexOf('@');
        if (schemeSeparator >= 0 && at > schemeSeparator) {
            return brokerUrl.substring(0, schemeSeparator + 3) + "***@" + brokerUrl.substring(at + 1);
        }
        return brokerUrl;
    }
}
