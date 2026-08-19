package com.procel.api.dto.analytics;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AggregationJobDTOs {
    private AggregationJobDTOs() {
    }

    public record CreateAggregationJobRequest(
            @NotNull Instant from,
            @NotNull Instant to,
            @NotNull Duration windowDuration,
            String sensorExternalId,
            String compartimentoId
    ) {
    }

    public record AggregationJobResponse(
            UUID id,
            String status,
            Instant from,
            Instant to,
            Duration windowDuration,
            String sensorExternalId,
            String compartimentoId,
            String requestedBy,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            String error,
            Progress progress,
            List<AggregationWindowResponse> windows
    ) {
    }

    public record AggregationWindowResponse(
            UUID id,
            int index,
            Instant from,
            Instant to,
            String status,
            int attempts,
            Instant nextAttemptAt,
            Instant lockedAt,
            String lockedBy,
            Instant startedAt,
            Instant completedAt,
            String error
    ) {
    }

    public record Progress(
            int totalWindows,
            int completedWindows,
            int failedWindows,
            int processingWindows
    ) {
    }
}
