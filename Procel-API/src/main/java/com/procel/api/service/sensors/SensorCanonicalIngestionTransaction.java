package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.MedicaoIngestaoMetadata;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.repository.sensors.MedicaoIngestaoMetadataRepository;
import com.procel.api.repository.sensors.SensorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SensorCanonicalIngestionTransaction {
    private final SensorRepository sensorRepo;
    private final MedicaoIngestaoMetadataRepository metadataRepo;
    private final SensorIngestionService sensorIngestionService;
    private final PayloadFingerprintService fingerprintService;

    public SensorCanonicalIngestionTransaction(
            SensorRepository sensorRepo,
            MedicaoIngestaoMetadataRepository metadataRepo,
            SensorIngestionService sensorIngestionService,
            PayloadFingerprintService fingerprintService
    ) {
        this.sensorRepo = sensorRepo;
        this.metadataRepo = metadataRepo;
        this.sensorIngestionService = sensorIngestionService;
        this.fingerprintService = fingerprintService;
    }

    @Transactional
    public SensorIngestDTOs.CanonicalIngestResponse ingestNew(
            String producerId,
            SensorIngestDTOs.CanonicalIngestRequest request
    ) {
        var sensor = sensorRepo.findByExternalIdAndAtivoTrue(request.sensorExternalId())
                .orElseThrow(() -> new ApiStatusException(
                        HttpStatus.NOT_FOUND,
                        "SENSOR_NOT_FOUND",
                        "Active sensor not found: " + request.sensorExternalId()
                ));

        Instant apiReceivedAt = Instant.now();
        String fingerprint = fingerprintService.fingerprint(request);
        var metadata = metadataRepo.saveAndFlush(new MedicaoIngestaoMetadata(
                producerId,
                sensor,
                request.messageId(),
                request.source(),
                request.sourceReceivedAt(),
                apiReceivedAt,
                fingerprint
        ));

        Medicao medicao = sensorIngestionService.ingestAndReturn(new RawSensorEvent(
                request.sensorExternalId(),
                request.timestamp(),
                apiReceivedAt,
                request.source().name(),
                request.values()
        ));

        metadata.complete(medicao, Instant.now());
        metadataRepo.save(metadata);

        return SensorIngestDTOs.CanonicalIngestResponse.created(
                medicao.getId(),
                request.messageId(),
                apiReceivedAt
        );
    }
}
