package com.procel.api.dto.sensors;

import com.procel.api.entity.sensors.MedicaoIngestaoSource;
import com.procel.api.entity.sensors.SensorIntegrationParserStatus;
import com.procel.api.entity.sensors.SensorResolutionMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SensorIntegrationAdminDTOs {
    private SensorIntegrationAdminDTOs() {}

    public record ProfileRequest(String nome, String descricao, MedicaoIngestaoSource source) {}
    public record ProfileUpdateRequest(String nome, String descricao, MedicaoIngestaoSource source) {}
    public record ProfileResponse(
            UUID id,
            String nome,
            String descricao,
            MedicaoIngestaoSource source,
            boolean ativo,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record MappingRequest(String parameterName, String valuePointer, boolean required) {}
    public record MappingResponse(UUID id, String parameterName, String valuePointer, boolean required) {}

    public record ParserVersionRequest(
            SensorResolutionMode sensorResolutionMode,
            String messageIdPointer,
            String sensorExternalIdPointer,
            String timestampPointer,
            String sourceReceivedAtPointer,
            String timestampFormat,
            List<MappingRequest> valueMappings
    ) {}

    public record ActivationRequest(UUID expectedActiveVersionId) {}

    public record ParserVersionResponse(
            UUID id,
            UUID profileId,
            int version,
            SensorIntegrationParserStatus status,
            SensorResolutionMode sensorResolutionMode,
            String messageIdPointer,
            String sensorExternalIdPointer,
            String timestampPointer,
            String sourceReceivedAtPointer,
            String timestampFormat,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt,
            List<MappingResponse> valueMappings
    ) {}

    public record BindingRequest(String sensorExternalId) {}
    public record BindingResponse(
            UUID id,
            UUID profileId,
            String sensorExternalId,
            String sensorNome,
            boolean ativo,
            Instant createdAt,
            Instant deactivatedAt
    ) {}
}
