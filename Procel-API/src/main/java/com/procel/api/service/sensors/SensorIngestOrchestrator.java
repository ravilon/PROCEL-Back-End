package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.dto.sensors.SensorTelemetryIngestDTOs;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SensorIngestOrchestrator {
    private final SensorCanonicalIngestionTransaction ingestionTransaction;
    private final SensorIngestDuplicateReader duplicateReader;
    private final IdempotencyConstraintInspector constraintInspector;
    private final PayloadFingerprintService fingerprintService;

    public SensorIngestOrchestrator(
            SensorCanonicalIngestionTransaction ingestionTransaction,
            SensorIngestDuplicateReader duplicateReader,
            IdempotencyConstraintInspector constraintInspector,
            PayloadFingerprintService fingerprintService
    ) {
        this.ingestionTransaction = ingestionTransaction;
        this.duplicateReader = duplicateReader;
        this.constraintInspector = constraintInspector;
        this.fingerprintService = fingerprintService;
    }

    public IngestOutcome ingest(String producerId, SensorIngestDTOs.CanonicalIngestRequest request) {
        try {
            return new IngestOutcome(HttpStatus.CREATED, ingestionTransaction.ingestNew(producerId, request));
        } catch (DataIntegrityViolationException ex) {
            if (constraintInspector.idempotencyConstraint(ex) != IdempotencyConstraintInspector.IdempotencyConstraint.DIRECT) {
                throw ex;
            }
            return duplicateOutcome(
                    duplicateReader.findByProducerSensorMessage(producerId, request.sensorExternalId(), request.messageId()),
                    request
            );
        }
    }

    public IngestOutcome ingestWithProfile(
            UUID integrationProfileId,
            UUID parserVersionId,
            String producerId,
            SensorIngestDTOs.CanonicalIngestRequest request
    ) {
        try {
            return new IngestOutcome(
                    HttpStatus.CREATED,
                    ingestionTransaction.ingestProfileNew(integrationProfileId, parserVersionId, producerId, request)
            );
        } catch (DataIntegrityViolationException ex) {
            if (constraintInspector.idempotencyConstraint(ex) != IdempotencyConstraintInspector.IdempotencyConstraint.PROFILE) {
                throw ex;
            }
            return duplicateOutcome(
                    duplicateReader.findByProfileSensorMessage(integrationProfileId, request.sensorExternalId(), request.messageId()),
                    request
            );
        }
    }

    public IngestOutcome ingestTelemetryRawWithProfile(
            UUID integrationProfileId,
            UUID parserVersionId,
            String serviceProducerId,
            SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest rawContext,
            SensorIngestDTOs.CanonicalIngestRequest request
    ) {
        try {
            return new IngestOutcome(
                    HttpStatus.CREATED,
                    ingestionTransaction.ingestTelemetryRawProfileNew(
                            integrationProfileId,
                            parserVersionId,
                            serviceProducerId,
                            rawContext,
                            request
                    )
            );
        } catch (DataIntegrityViolationException ex) {
            if (constraintInspector.idempotencyConstraint(ex) != IdempotencyConstraintInspector.IdempotencyConstraint.TELEMETRY_RAW) {
                throw ex;
            }
            return duplicateOutcome(
                    duplicateReader.findByTelemetryRawKey(
                            integrationProfileId,
                            request.sensorExternalId(),
                            rawContext.originalProducerId(),
                            rawContext.rawMessageId()
                    ),
                    request
            );
        }
    }

    private IngestOutcome duplicateOutcome(
            SensorIngestDuplicateReader.DuplicateLookupResult duplicate,
            SensorIngestDTOs.CanonicalIngestRequest request
    ) {
        String incomingFingerprint = fingerprintService.fingerprint(request);
        if (incomingFingerprint.equals(duplicate.payloadFingerprint())) {
            return new IngestOutcome(
                    HttpStatus.OK,
                    SensorIngestDTOs.CanonicalIngestResponse.duplicate(
                            duplicate.medicaoId(),
                            duplicate.messageId(),
                            duplicate.originalApiReceivedAt(),
                            duplicate.detectedAt()
                    )
            );
        }
        return new IngestOutcome(
                HttpStatus.CONFLICT,
                SensorIngestDTOs.CanonicalIngestResponse.conflict(
                        duplicate.medicaoId(),
                        duplicate.messageId(),
                        duplicate.originalApiReceivedAt(),
                        duplicate.detectedAt()
                )
        );
    }

    public record IngestOutcome(HttpStatus status, SensorIngestDTOs.CanonicalIngestResponse response) {}
}
