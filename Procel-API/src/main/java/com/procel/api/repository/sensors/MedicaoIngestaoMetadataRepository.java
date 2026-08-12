package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.MedicaoIngestaoMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

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
}
