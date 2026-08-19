package com.procel.telemetry.service.mqtt;

import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class MqttTopicParser {
    public TopicContext parse(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new MqttTelemetryPermanentException("MQTT_TOPIC_INVALID", "MQTT topic is required.");
        }
        String[] parts = topic.split("/", -1);
        if (parts.length == 6
                && "procel".equals(parts[0])
                && "telemetry".equals(parts[1])
                && "v1".equals(parts[2])
                && "events".equals(parts[5])) {
            return new TopicContext(required(parts[3], "producerId"), required(parts[4], "sensorId"));
        }
        if (parts.length == 5
                && "procel".equals(parts[0])
                && "telemetry".equals(parts[1])
                && "v1".equals(parts[2])
                && "events".equals(parts[4])) {
            return new TopicContext(required(parts[3], "producerId"), null);
        }
        throw new MqttTelemetryPermanentException("MQTT_TOPIC_INVALID", "MQTT topic does not match supported filters.");
    }

    private static String required(String value, String field) {
        String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8).trim();
        if (decoded.isBlank() || decoded.contains("/")) {
            throw new MqttTelemetryPermanentException("MQTT_TOPIC_INVALID", field + " in MQTT topic is invalid.");
        }
        return decoded;
    }

    public record TopicContext(String producerId, String sensorId) {}
}
