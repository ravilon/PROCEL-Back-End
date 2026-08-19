package com.procel.telemetry.service.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttPayloadAdapterTest {
    private final MqttPayloadAdapter adapter = new MqttPayloadAdapter(new ObjectMapper());

    @Test
    void adaptsEnvelopeToTelemetryIngestContractWithMqttSource() {
        var request = adapter.toTelemetryRequest("""
                {"messageId":"msg-1","sensorId":"sensor-1","sourceTimestamp":"2026-08-19T12:00:00Z","payload":{"value":1}}
                """.getBytes(StandardCharsets.UTF_8), new MqttTopicParser.TopicContext("producer-a", "sensor-1"));

        assertThat(request.get("source").asText()).isEqualTo("MQTT");
        assertThat(request.get("messageId").asText()).isEqualTo("msg-1");
        assertThat(request.get("sensorId").asText()).isEqualTo("sensor-1");
        assertThat(request.get("payload").get("value").asInt()).isEqualTo(1);
    }

    @Test
    void topicSensorPrevalesAndDivergenceIsRejected() {
        assertThatThrownBy(() -> adapter.toTelemetryRequest("""
                {"messageId":"msg-1","sensorId":"sensor-other","payload":{"value":1}}
                """.getBytes(StandardCharsets.UTF_8), new MqttTopicParser.TopicContext("producer-a", "sensor-topic")))
                .isInstanceOf(MqttTelemetryPermanentException.class)
                .extracting("code")
                .isEqualTo("MQTT_SENSOR_CONFLICT");
    }

    @Test
    void requiresMessageId() {
        assertThatThrownBy(() -> adapter.toTelemetryRequest("""
                {"payload":{"value":1}}
                """.getBytes(StandardCharsets.UTF_8), new MqttTopicParser.TopicContext("producer-a", null)))
                .isInstanceOf(MqttTelemetryPermanentException.class)
                .extracting("code")
                .isEqualTo("MESSAGE_ID_REQUIRED");
    }
}
