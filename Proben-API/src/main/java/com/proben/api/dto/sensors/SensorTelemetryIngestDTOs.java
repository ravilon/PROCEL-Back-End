package com.proben.api.dto.sensors;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Definicoes dos objetos de transferencia de dados de sensor telemetry ingest.
 */

public final class SensorTelemetryIngestDTOs {
    private SensorTelemetryIngestDTOs() {}

    public record TelemetryRawIntegrationIngestRequest(
            @NotBlank
            String rawTelemetryEventId,
            @NotBlank
            String originalProducerId,
            @NotBlank
            String rawMessageId,
            @NotNull
            Instant rawReceivedAt,
            Instant rawSourceTimestamp,
            @NotNull
            JsonNode payload
    ) {}
}
