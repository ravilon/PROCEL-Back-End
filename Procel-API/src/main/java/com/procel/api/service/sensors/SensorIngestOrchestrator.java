package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIngestDTOs;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
            return new IngestOutcome(
                    HttpStatus.CREATED,
                    ingestionTransaction.ingestNew(producerId, request)
            );
        } catch (DataIntegrityViolationException ex) {
            if (!constraintInspector.isIdempotencyUniqueViolation(ex)) {
                throw ex;
            }
            var duplicate = duplicateReader.findByProducerSensorMessage(
                    producerId,
                    request.sensorExternalId(),
                    request.messageId()
            );
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
    }

    public record IngestOutcome(HttpStatus status, SensorIngestDTOs.CanonicalIngestResponse response) {}
}
