package com.procel.telemetry.service.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import com.procel.telemetry.entity.TelemetrySource;

import java.time.Instant;
import java.util.List;

public final class CanonicalApiDTOs {
    private CanonicalApiDTOs() {}

    public record SnapshotResponse(int version, Instant generatedAt, List<ProfileSnapshot> profiles) {}

    public record ProfileSnapshot(
            String id,
            String nome,
            TelemetrySource source,
            ParserVersionSnapshot activeParserVersion,
            List<BindingSnapshot> bindings
    ) {}

    public record ParserVersionSnapshot(
            String id,
            int version,
            SensorResolutionMode sensorResolutionMode,
            String messageIdPointer,
            String sensorExternalIdPointer,
            String timestampPointer,
            String sourceReceivedAtPointer,
            String timestampFormat,
            List<MappingSnapshot> valueMappings
    ) {}

    public record MappingSnapshot(String parameterName, String valuePointer, boolean required) {}
    public record BindingSnapshot(String sensorExternalId, String sensorNome) {}

    public enum SensorResolutionMode {
        ROUTE_SENSOR,
        PAYLOAD_POINTER
    }

    public record TelemetryRawIntegrationIngestRequest(
            String rawTelemetryEventId,
            String originalProducerId,
            String rawMessageId,
            Instant rawReceivedAt,
            Instant rawSourceTimestamp,
            Object payload
    ) {}

    public record CanonicalIngestResponse(
            String status,
            String code,
            boolean duplicate,
            String medicaoId,
            String messageId,
            Instant apiReceivedAt,
            Instant originalApiReceivedAt,
            Instant duplicateDetectedAt,
            Instant conflictDetectedAt,
            String message
    ) {}

    public record ErrorResponse(String message, String error, Instant timestamp) {}
}
