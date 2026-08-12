package com.procel.api.dto.sensors;

import com.procel.api.entity.sensors.MedicaoIngestaoSource;
import com.procel.api.entity.sensors.SensorResolutionMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SensorIntegrationSnapshotDTOs {
    private SensorIntegrationSnapshotDTOs() {}

    public record SnapshotResponse(int version, Instant generatedAt, List<ProfileSnapshot> profiles) {}

    public record ProfileSnapshot(
            UUID id,
            String nome,
            MedicaoIngestaoSource source,
            ParserVersionSnapshot activeParserVersion,
            List<BindingSnapshot> bindings
    ) {}

    public record ParserVersionSnapshot(
            UUID id,
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
}
