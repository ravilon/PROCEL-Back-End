package com.procel.api.dto.sensors;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

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
