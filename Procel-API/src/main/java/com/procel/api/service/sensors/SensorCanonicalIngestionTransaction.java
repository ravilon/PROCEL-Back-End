package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.entity.sensors.*;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.repository.sensors.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SensorCanonicalIngestionTransaction {
    private final SensorRepository sensorRepo;
    private final MedicaoIngestaoMetadataRepository metadataRepo;
    private final SensorIngestionService sensorIngestionService;
    private final PayloadFingerprintService fingerprintService;
    private final SensorIntegrationProfileRepository profileRepo;
    private final SensorIntegrationParserVersionRepository parserVersionRepo;
    private final SensorIntegrationBindingRepository bindingRepo;

    public SensorCanonicalIngestionTransaction(
            SensorRepository sensorRepo,
            MedicaoIngestaoMetadataRepository metadataRepo,
            SensorIngestionService sensorIngestionService,
            PayloadFingerprintService fingerprintService,
            SensorIntegrationProfileRepository profileRepo,
            SensorIntegrationParserVersionRepository parserVersionRepo,
            SensorIntegrationBindingRepository bindingRepo
    ) {
        this.sensorRepo = sensorRepo;
        this.metadataRepo = metadataRepo;
        this.sensorIngestionService = sensorIngestionService;
        this.fingerprintService = fingerprintService;
        this.profileRepo = profileRepo;
        this.parserVersionRepo = parserVersionRepo;
        this.bindingRepo = bindingRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        return persistMeasurement(producerId, sensor, request, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SensorIngestDTOs.CanonicalIngestResponse ingestProfileNew(
            UUID integrationProfileId,
            UUID parserVersionId,
            String producerId,
            SensorIngestDTOs.CanonicalIngestRequest request
    ) {
        var profile = profileRepo.findById(integrationProfileId)
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                        "Integration profile not found: " + integrationProfileId));
        if (!profile.isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "PROFILE_INACTIVE", "Integration profile is inactive.");
        }

        var parserVersion = parserVersionRepo.findById(parserVersionId)
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "PARSER_VERSION_NOT_FOUND",
                        "Parser version not found: " + parserVersionId));
        if (!parserVersion.getProfile().getId().equals(integrationProfileId)) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_PROFILE_MISMATCH",
                    "Parser version does not belong to profile.");
        }
        var currentActive = parserVersionRepo.findByProfile_IdAndStatus(
                        integrationProfileId,
                        SensorIntegrationParserStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_CHANGED",
                        "Active parser version changed."));
        if (!currentActive.getId().equals(parserVersionId)) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_CHANGED",
                    "Active parser version changed.");
        }
        if (parserVersion.getStatus() != SensorIntegrationParserStatus.ACTIVE) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_NOT_ACTIVE",
                    "Parser version is no longer active.");
        }

        Sensor sensor = sensorRepo.findByExternalId(request.sensorExternalId())
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "SENSOR_NOT_FOUND",
                        "Sensor not found: " + request.sensorExternalId()));
        if (!sensor.isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "SENSOR_INACTIVE", "Sensor is inactive.");
        }

        var binding = bindingRepo.findFirstByProfile_IdAndSensor_ExternalIdOrderByCreatedAtDesc(
                integrationProfileId,
                request.sensorExternalId()
        );
        if (binding.isEmpty()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "BINDING_NOT_FOUND",
                    "Binding not found for profile and sensor.");
        }
        if (!binding.get().isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "BINDING_INACTIVE", "Binding is inactive.");
        }

        return persistMeasurement(producerId, sensor, request, integrationProfileId, parserVersionId);
    }

    private SensorIngestDTOs.CanonicalIngestResponse persistMeasurement(
            String producerId,
            Sensor sensor,
            SensorIngestDTOs.CanonicalIngestRequest request,
            UUID integrationProfileId,
            UUID parserVersionId
    ) {
        Instant apiReceivedAt = Instant.now();
        String fingerprint = fingerprintService.fingerprint(request);
        var metadata = metadataRepo.saveAndFlush(new MedicaoIngestaoMetadata(
                producerId,
                sensor,
                request.messageId(),
                request.source(),
                request.sourceReceivedAt(),
                apiReceivedAt,
                fingerprint,
                integrationProfileId,
                parserVersionId
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
