package com.procel.telemetry.service.mqtt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttTopicParserTest {
    private final MqttTopicParser parser = new MqttTopicParser();

    @Test
    void parsesTopicWithSensor() {
        MqttTopicParser.TopicContext context = parser.parse("procel/telemetry/v1/producer-a/sensor-1/events");

        assertThat(context.producerId()).isEqualTo("producer-a");
        assertThat(context.sensorId()).isEqualTo("sensor-1");
    }

    @Test
    void parsesTopicWithoutSensor() {
        MqttTopicParser.TopicContext context = parser.parse("procel/telemetry/v1/producer-a/events");

        assertThat(context.producerId()).isEqualTo("producer-a");
        assertThat(context.sensorId()).isNull();
    }

    @Test
    void rejectsInvalidTopic() {
        assertThatThrownBy(() -> parser.parse("procel/telemetry/v1/events"))
                .isInstanceOf(MqttTelemetryPermanentException.class)
                .extracting("code")
                .isEqualTo("MQTT_TOPIC_INVALID");
    }
}
