package com.procel.api.service.sensors;

import com.procel.api.exception.ApiStatusException;
import com.procel.api.repository.sensors.MedicaoIngestaoMetadataRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SensorIngestDuplicateReader {
    private final MedicaoIngestaoMetadataRepository metadataRepo;

    public SensorIngestDuplicateReader(MedicaoIngestaoMetadataRepository metadataRepo) {
        this.metadataRepo = metadataRepo;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public DuplicateLookupResult findByProducerSensorMessage(
            String producerId,
            String sensorExternalId,
            String messageId
    ) {
        var metadata = metadataRepo.findByProducerIdAndSensor_ExternalIdAndMessageId(
                        producerId,
                        sensorExternalId,
                        messageId
                )
                .orElseThrow(() -> new ApiStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "IDEMPOTENCY_LOOKUP_UNAVAILABLE",
                        "Idempotency conflict was detected but the winning row could not be loaded."
                ));

        UUID medicaoId = metadata.getMedicao() != null ? metadata.getMedicao().getId() : null;
        return new DuplicateLookupResult(
                medicaoId,
                metadata.getMessageId(),
                metadata.getPayloadFingerprint(),
                metadata.getApiReceivedAt(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public DuplicateLookupResult findByProfileSensorMessage(
            UUID integrationProfileId,
            String sensorExternalId,
            String messageId
    ) {
        var metadata = metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndMessageId(
                        integrationProfileId,
                        sensorExternalId,
                        messageId
                )
                .orElseThrow(() -> new ApiStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "IDEMPOTENCY_LOOKUP_UNAVAILABLE",
                        "Idempotency conflict was detected but the winning row could not be loaded."
                ));

        UUID medicaoId = metadata.getMedicao() != null ? metadata.getMedicao().getId() : null;
        return new DuplicateLookupResult(
                medicaoId,
                metadata.getMessageId(),
                metadata.getPayloadFingerprint(),
                metadata.getApiReceivedAt(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public DuplicateLookupResult findByTelemetryRawKey(
            UUID integrationProfileId,
            String sensorExternalId,
            String originalProducerId,
            String rawMessageId
    ) {
        var metadata = metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndOriginalProducerIdAndRawMessageId(
                        integrationProfileId,
                        sensorExternalId,
                        originalProducerId,
                        rawMessageId
                )
                .orElseThrow(() -> new ApiStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "IDEMPOTENCY_LOOKUP_UNAVAILABLE",
                        "Idempotency conflict was detected but the winning row could not be loaded."
                ));

        UUID medicaoId = metadata.getMedicao() != null ? metadata.getMedicao().getId() : null;
        return new DuplicateLookupResult(
                medicaoId,
                metadata.getMessageId(),
                metadata.getPayloadFingerprint(),
                metadata.getApiReceivedAt(),
                Instant.now()
        );
    }

    public record DuplicateLookupResult(
            UUID medicaoId,
            String messageId,
            String payloadFingerprint,
            Instant originalApiReceivedAt,
            Instant detectedAt
    ) {}
}
