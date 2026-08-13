package com.procel.api.service.sensors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.procel.api.config.SensorIntegrationParserProperties;
import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.entity.sensors.MedicaoIngestaoSource;
import com.procel.api.entity.sensors.SensorIntegrationParserVersion;
import com.procel.api.entity.sensors.SensorResolutionMode;
import com.procel.api.exception.ApiStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SensorExternalPayloadParser {
    private final SensorIntegrationParserProperties properties;

    public SensorExternalPayloadParser(SensorIntegrationParserProperties properties) {
        this.properties = properties;
    }

    public SensorIngestDTOs.CanonicalIngestRequest parse(
            JsonNode payload,
            SensorIntegrationParserVersion version,
            MedicaoIngestaoSource source,
            String routeSensorExternalId
    ) {
        if (payload == null || payload.isMissingNode()) {
            throw bad("Payload is required");
        }
        validateDepth(payload, 0);
        String messageId = requiredText(payload, version.getMessageIdPointer(), "MESSAGE_ID_INVALID", "messageId");
        String sensorExternalId = resolveSensor(payload, version, routeSensorExternalId);
        Instant timestamp = parseInstant(requiredText(payload, version.getTimestampPointer(), "TIMESTAMP_INVALID", "timestamp"), "TIMESTAMP_INVALID");
        Instant sourceReceivedAt = null;
        if (version.getSourceReceivedAtPointer() != null && !version.getSourceReceivedAtPointer().isBlank()) {
            JsonNode sourceReceivedNode = payload.at(version.getSourceReceivedAtPointer());
            if (!sourceReceivedNode.isMissingNode() && !sourceReceivedNode.isNull()) {
                sourceReceivedAt = parseInstant(textNode(sourceReceivedNode, "SOURCE_RECEIVED_AT_INVALID", "sourceReceivedAt"), "SOURCE_RECEIVED_AT_INVALID");
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (var mapping : version.getValueMappings()) {
            JsonNode value = payload.at(mapping.getValuePointer());
            if (value instanceof MissingNode || value.isMissingNode()) {
                if (mapping.isRequired()) {
                    throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "MAPPING_REQUIRED_MISSING",
                            "Required mapping is missing: " + mapping.getParameterName());
                }
                continue;
            }
            values.put(mapping.getParameterName(), scalarValue(value));
        }
        if (values.isEmpty()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "VALUES_EMPTY", "No values were parsed from payload.");
        }
        return new SensorIngestDTOs.CanonicalIngestRequest(
                messageId,
                sensorExternalId,
                timestamp,
                source,
                sourceReceivedAt,
                values
        );
    }

    private String resolveSensor(JsonNode payload, SensorIntegrationParserVersion version, String routeSensorExternalId) {
        if (version.getSensorResolutionMode() == SensorResolutionMode.ROUTE_SENSOR) {
            if (routeSensorExternalId == null || routeSensorExternalId.isBlank()) {
                throw new ApiStatusException(HttpStatus.BAD_REQUEST, "SENSOR_ROUTE_REQUIRED", "sensorExternalId route parameter is required.");
            }
            return routeSensorExternalId.trim();
        }
        if (routeSensorExternalId != null && !routeSensorExternalId.isBlank()) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "SENSOR_RESOLUTION_CONFLICT", "Route sensor is not allowed for PAYLOAD_POINTER parser.");
        }
        return requiredText(payload, version.getSensorExternalIdPointer(), "SENSOR_EXTERNAL_ID_INVALID", "sensorExternalId");
    }

    private Object scalarValue(JsonNode value) {
        if (value.isNull()) return null;
        if (value.isBoolean()) return value.booleanValue();
        if (value.isNumber()) return value.decimalValue().stripTrailingZeros();
        if (value.isTextual()) return value.textValue();
        throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "VALUE_NOT_SCALAR", "Mapped value must be scalar.");
    }

    private String requiredText(JsonNode payload, String pointer, String error, String field) {
        JsonNode node = payload.at(pointer);
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, error, field + " is required.");
        }
        return textNode(node, error, field);
    }

    private String textNode(JsonNode node, String error, String field) {
        if (!node.isTextual()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, error, field + " must be a string.");
        }
        String value = node.textValue().trim();
        if (value.isBlank()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, error, field + " must not be blank.");
        }
        return value;
    }

    private Instant parseInstant(String value, String error) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, error, "Timestamp must be ISO-8601 instant.");
        }
    }

    private void validateDepth(JsonNode node, int depth) {
        if (depth > properties.getMaxDepth()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "PAYLOAD_TOO_DEEP", "Integration payload is too deep.");
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                validateDepth(child, depth + 1);
            }
        }
    }

    private static ApiStatusException bad(String message) {
        return new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
