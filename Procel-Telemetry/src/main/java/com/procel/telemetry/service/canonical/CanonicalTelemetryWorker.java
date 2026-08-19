package com.procel.telemetry.service.canonical;

import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.entity.RawTelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "procel.telemetry.canonical-worker", name = "enabled", havingValue = "true")
public class CanonicalTelemetryWorker {
    private static final Logger log = LoggerFactory.getLogger(CanonicalTelemetryWorker.class);

    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
    private final RawTelemetryClaimService claimService;
    private final SensorIntegrationSnapshotClient snapshotClient;
    private final SensorIntegrationProfileSelector profileSelector;
    private final CanonicalIngestClient ingestClient;
    private final TelemetryProperties properties;

    public CanonicalTelemetryWorker(
            RawTelemetryClaimService claimService,
            SensorIntegrationSnapshotClient snapshotClient,
            SensorIntegrationProfileSelector profileSelector,
            CanonicalIngestClient ingestClient,
            TelemetryProperties properties
    ) {
        this.claimService = claimService;
        this.snapshotClient = snapshotClient;
        this.profileSelector = profileSelector;
        this.ingestClient = ingestClient;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${procel.telemetry.canonical-worker.poll-interval:PT5S}")
    public void tick() {
        processBatch();
    }

    int processBatch() {
        claimService.recoverStuck(workerId, Instant.now());
        int processed = 0;
        for (int i = 0; i < properties.getCanonicalWorker().getBatchSize(); i++) {
            if (!processOne()) {
                break;
            }
            processed++;
        }
        return processed;
    }

    public boolean processOne() {
        Instant now = Instant.now();
        RawTelemetryEvent event = claimService.claimNext(workerId, now);
        if (event == null) {
            return false;
        }
        processClaimed(event, now);
        return true;
    }

    private void processClaimed(RawTelemetryEvent event, Instant now) {
        CanonicalApiDTOs.ProfileSnapshot profile = null;
        try {
            CanonicalApiDTOs.SnapshotResponse snapshot = snapshotClient.snapshot();
            profile = profileSelector.select(event, snapshot);
            CanonicalApiDTOs.CanonicalIngestResponse response = ingestClient.ingest(event, profile);
            applyResponse(event, profile, response);
        } catch (ProfileSelectionException ex) {
            claimService.markFailed(event, ex.getCode());
            log.info("raw telemetry event failed profile selection: rawEventId={}, messageId={}, source={}, sensorId={}, code={}",
                    event.getId(), event.getMessageId(), event.getSource(), event.getSensorId(), ex.getCode());
        } catch (TransientCanonicalException ex) {
            claimService.retryOrFail(event, ex.getMessage(), now);
            log.info("raw telemetry event scheduled for retry: rawEventId={}, messageId={}, attempts={}, error={}",
                    event.getId(), event.getMessageId(), event.getProcessing().getAttempts(), ex.getMessage());
        } catch (CanonicalHttpException ex) {
            claimService.markFailed(event, profile, ex.getErrorCode());
            log.info("raw telemetry event failed canonical HTTP call: rawEventId={}, messageId={}, httpStatus={}, code={}",
                    event.getId(), event.getMessageId(), ex.getStatusCode(), ex.getErrorCode());
        } catch (RuntimeException ex) {
            claimService.retryOrFail(event, "WORKER_UNEXPECTED_ERROR", now);
            log.warn("raw telemetry event failed with unexpected error: rawEventId={}, messageId={}",
                    event.getId(), event.getMessageId(), ex);
        }
    }

    private void applyResponse(
            RawTelemetryEvent event,
            CanonicalApiDTOs.ProfileSnapshot profile,
            CanonicalApiDTOs.CanonicalIngestResponse response
    ) {
        if ("MEASUREMENT_INGESTED".equals(response.code()) && response.medicaoId() != null) {
            claimService.markAccepted(event, profile, response.medicaoId());
            return;
        }
        if ("DUPLICATE_MESSAGE".equals(response.code())) {
            claimService.markDuplicate(event, profile, response.medicaoId());
            return;
        }
        if ("IDEMPOTENCY_CONFLICT".equals(response.code())) {
            claimService.markConflict(event, profile, response.medicaoId(), response.code());
            return;
        }
        claimService.markFailed(event, profile, response.code() != null ? response.code() : "CANONICAL_RESPONSE_UNHANDLED");
    }
}
