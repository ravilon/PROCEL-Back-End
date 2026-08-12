package com.procel.api.dto.sensors;

import com.procel.api.entity.sensors.MedicaoIngestaoSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SensorIngestDTOs {
    private SensorIngestDTOs() {}

    @Schema(description = "Medicao canonica ja parseada por um produtor autorizado.")
    public record CanonicalIngestRequest(
            @NotBlank
            String messageId,
            @NotBlank
            String sensorExternalId,
            @NotNull
            Instant timestamp,
            @NotNull
            MedicaoIngestaoSource source,
            Instant sourceReceivedAt,
            @NotEmpty
            Map<String, Object> values
    ) {}

    public record CanonicalIngestResponse(
            String status,
            String code,
            boolean duplicate,
            UUID medicaoId,
            String messageId,
            Instant apiReceivedAt,
            Instant originalApiReceivedAt,
            Instant duplicateDetectedAt,
            Instant conflictDetectedAt,
            String message
    ) {
        public static CanonicalIngestResponse created(UUID medicaoId, String messageId, Instant apiReceivedAt) {
            return new CanonicalIngestResponse(
                    "CREATED",
                    "MEASUREMENT_INGESTED",
                    false,
                    medicaoId,
                    messageId,
                    apiReceivedAt,
                    null,
                    null,
                    null,
                    null
            );
        }

        public static CanonicalIngestResponse duplicate(
                UUID medicaoId,
                String messageId,
                Instant originalApiReceivedAt,
                Instant duplicateDetectedAt
        ) {
            return new CanonicalIngestResponse(
                    "DUPLICATE",
                    "DUPLICATE_MESSAGE",
                    true,
                    medicaoId,
                    messageId,
                    null,
                    originalApiReceivedAt,
                    duplicateDetectedAt,
                    null,
                    null
            );
        }

        public static CanonicalIngestResponse conflict(
                UUID medicaoId,
                String messageId,
                Instant originalApiReceivedAt,
                Instant conflictDetectedAt
        ) {
            return new CanonicalIngestResponse(
                    "CONFLICT",
                    "IDEMPOTENCY_CONFLICT",
                    true,
                    medicaoId,
                    messageId,
                    null,
                    originalApiReceivedAt,
                    null,
                    conflictDetectedAt,
                    "A different payload was already ingested for this producer, sensor and messageId."
            );
        }
    }
}
