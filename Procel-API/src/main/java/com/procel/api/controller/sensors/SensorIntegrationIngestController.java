package com.procel.api.controller.sensors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.service.sensors.SensorIntegrationIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/sensors")
public class SensorIntegrationIngestController {
    private final SensorIntegrationIngestService service;
    private final ObjectMapper objectMapper;

    public SensorIntegrationIngestController(SensorIntegrationIngestService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/ingest/integrations/{profileId}")
    public ResponseEntity<SensorIngestDTOs.CanonicalIngestResponse> ingestByPayloadSensor(
            Authentication authentication,
            @PathVariable UUID profileId,
            @RequestBody String payload
    ) {
        var outcome = service.ingestPayload(profileId, null, producer(authentication), readPayload(payload));
        return ResponseEntity.status(outcome.status()).body(outcome.response());
    }

    @PostMapping("/{sensorExternalId}/ingest/integrations/{profileId}")
    public ResponseEntity<SensorIngestDTOs.CanonicalIngestResponse> ingestByRouteSensor(
            Authentication authentication,
            @PathVariable String sensorExternalId,
            @PathVariable UUID profileId,
            @RequestBody String payload
    ) {
        var outcome = service.ingestPayload(profileId, sensorExternalId, producer(authentication), readPayload(payload));
        return ResponseEntity.status(outcome.status()).body(outcome.response());
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException ex) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Invalid JSON payload.");
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
