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
            RawTelemetryEvent.Reprocessing reprocessing,
            List<RawTelemetryEvent.ReprocessAuditEntry> reprocessAudit,
            Instant expiresAt
    ) {}

    public record ReprocessRequest(String reason) {}

    public record ReprocessResponse(
            String id,
            RawTelemetryStatus status,
            RawTelemetryStatus previousStatus,
            int reprocessCount,
            String requestedBy,
            Instant requestedAt
    ) {}

    public record EventPageResponse(
            List<EventResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
