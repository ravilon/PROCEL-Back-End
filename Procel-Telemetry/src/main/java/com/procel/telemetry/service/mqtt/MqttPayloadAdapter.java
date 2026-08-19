package com.procel.telemetry.service.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MqttPayloadAdapter {
    private final ObjectMapper objectMapper;

    public MqttPayloadAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode toTelemetryRequest(byte[] payload, MqttTopicParser.TopicContext topicContext) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(payload);
        } catch (IOException ex) {
            throw new MqttTelemetryPermanentException("INVALID_JSON", "Invalid MQTT JSON payload.");
        }
        if (envelope == null || !envelope.isObject()) {
            throw new MqttTelemetryPermanentException("BAD_REQUEST", "MQTT payload must be a JSON object.");
        }

        String messageId = requiredText(envelope, "messageId");
        String envelopeSensorId = optionalText(envelope, "sensorId");
        String sensorId = topicContext.sensorId() != null ? topicContext.sensorId() : envelopeSensorId;
        if (topicContext.sensorId() != null
                && envelopeSensorId != null
                && !topicContext.sensorId().equals(envelopeSensorId)) {
            throw new MqttTelemetryPermanentException("MQTT_SENSOR_CONFLICT", "MQTT topic sensorId differs from envelope sensorId.");
        }
        if (!envelope.has("payload")) {
            throw new MqttTelemetryPermanentException("BAD_REQUEST", "payload is required.");
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("source", "MQTT");
        request.put("messageId", messageId);
        if (sensorId != null) {
            request.put("sensorId", sensorId);
        }
        if (envelope.has("sourceTimestamp")) {
            request.set("sourceTimestamp", envelope.get("sourceTimestamp"));
        }
        request.set("payload", envelope.get("payload"));
        return request;
    }

    private static String requiredText(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new MqttTelemetryPermanentException("MESSAGE_ID_REQUIRED", field + " is required.");
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw new MqttTelemetryPermanentException("BAD_REQUEST", field + " must be a string.");
        }
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }
}
