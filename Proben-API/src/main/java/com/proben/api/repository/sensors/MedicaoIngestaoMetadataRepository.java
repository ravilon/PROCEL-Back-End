package com.proben.api.repository.sensors;

import com.proben.api.entity.sensors.MedicaoIngestaoMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de medicao ingestao metadata.
 */

public interface MedicaoIngestaoMetadataRepository extends JpaRepository<MedicaoIngestaoMetadata, UUID> {
    Optional<MedicaoIngestaoMetadata> findByProducerIdAndSensor_ExternalIdAndMessageId(
            String producerId,
            String sensorExternalId,
            String messageId
    );

    Optional<MedicaoIngestaoMetadata> findByIntegrationProfileIdAndSensor_ExternalIdAndMessageId(
            UUID integrationProfileId,
            String sensorExternalId,
            String messageId
    );

    Optional<MedicaoIngestaoMetadata> findByIntegrationProfileIdAndSensor_ExternalIdAndOriginalProducerIdAndRawMessageId(
            UUID integrationProfileId,
            String sensorExternalId,
            String originalProducerId,
            String rawMessageId
    );
}
