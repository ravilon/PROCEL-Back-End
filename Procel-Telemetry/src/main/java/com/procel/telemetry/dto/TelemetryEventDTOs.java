package com.procel.telemetry.dto;

import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.entity.TelemetrySource;

import java.time.Instant;
import java.util.List;

public final class TelemetryEventDTOs {
    private TelemetryEventDTOs() {}

    public record IngestResponse(
            String id,
            RawTelemetryStatus status,
            boolean duplicate,
            Instant receivedAt
    ) {}

    public record EventResponse(
            String id,
            String producerId,
            TelemetrySource source,
            String messageId,
            String sensorId,
            Instant sourceTimestamp,
            Instant receivedAt,
            Object payload,
            String payloadHash,
            RawTelemetryStatus status,
            RawTelemetryEvent.Processing processing,
            Instant expiresAt
    ) {}

    public record EventPageResponse(
            List<EventResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
