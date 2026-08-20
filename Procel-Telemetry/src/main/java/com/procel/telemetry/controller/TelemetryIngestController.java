package com.procel.telemetry.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.exception.ApiStatusException;
import com.procel.telemetry.observability.TelemetryObservabilityMetrics;
import com.procel.telemetry.service.TelemetryIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("/api/telemetry/events")
public class TelemetryIngestController {
    private final TelemetryIngestService service;
    private final ObjectMapper objectMapper;
    private final TelemetryObservabilityMetrics metrics;

    public TelemetryIngestController(
            TelemetryIngestService service,
            ObjectMapper objectMapper,
            TelemetryObservabilityMetrics metrics
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @PostMapping
    public ResponseEntity<TelemetryEventDTOs.IngestResponse> ingest(
            Authentication authentication,
            @RequestBody String body
    ) {
        TelemetryEventDTOs.IngestResponse response = service.ingest(producer(authentication), readBody(body));
        return ResponseEntity.status(response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    private JsonNode readBody(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            metrics.event("UNKNOWN", "discarded", Duration.ZERO);
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Invalid JSON payload.");
        }
    }

    private String producer(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ApiStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token ausente ou invalido");
        }
        return authentication.getName();
    }
}
