package com.procel.api.controller.sensors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.dto.sensors.SensorTelemetryIngestDTOs;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.service.sensors.SensorTelemetryIntegrationIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/sensors/internal/telemetry-events")
public class SensorTelemetryInternalIngestController {
    /*
     * Internal contract for Procel-Telemetry. Secure short-lived service JWT issuance
     * is intentionally left to the worker integration stage.
     */
    private final SensorTelemetryIntegrationIngestService service;
    private final ObjectMapper objectMapper;

    public SensorTelemetryInternalIngestController(
            SensorTelemetryIntegrationIngestService service,
            ObjectMapper objectMapper
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/ingest/integrations/{profileId}")
    public ResponseEntity<SensorIngestDTOs.CanonicalIngestResponse> ingestByPayloadSensor(
            Authentication authentication,
            @PathVariable UUID profileId,
            @RequestBody String body
    ) {
        var request = readRequest(body);
        var outcome = service.ingestPayload(profileId, null, producer(authentication), request);
        return ResponseEntity.status(outcome.status()).body(outcome.response());
    }

    @PostMapping("/{sensorExternalId}/ingest/integrations/{profileId}")
    public ResponseEntity<SensorIngestDTOs.CanonicalIngestResponse> ingestByRouteSensor(
            Authentication authentication,
            @PathVariable String sensorExternalId,
            @PathVariable UUID profileId,
            @RequestBody String body
    ) {
        var request = readRequest(body);
        var outcome = service.ingestPayload(profileId, sensorExternalId, producer(authentication), request);
        return ResponseEntity.status(outcome.status()).body(outcome.response());
    }

    private SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest readRequest(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Invalid JSON payload.");
        }
        if (root == null || !root.isObject()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "body must be a JSON object");
        }
        return new SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest(
                requiredText(root, "rawTelemetryEventId"),
                requiredText(root, "originalProducerId"),
                requiredText(root, "rawMessageId"),
                requiredInstant(root, "rawReceivedAt"),
                optionalInstant(root, "rawSourceTimestamp"),
                root.get("payload")
        );
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", field + " is required");
        }
        return value.asText().trim();
    }

    private static Instant requiredInstant(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "TIMESTAMP_INVALID", field + " must be an ISO-8601 instant");
        }
        return parseInstant(value.asText(), field);
    }

    private static Instant optionalInstant(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "TIMESTAMP_INVALID", field + " must be an ISO-8601 instant");
        }
        return parseInstant(value.asText(), field);
    }

    private static Instant parseInstant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "TIMESTAMP_INVALID", field + " must be an ISO-8601 instant");
        }
    }

    private String producer(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ApiStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Token ausente ou invalido"
            );
        }
        return authentication.getName();
    }
}
