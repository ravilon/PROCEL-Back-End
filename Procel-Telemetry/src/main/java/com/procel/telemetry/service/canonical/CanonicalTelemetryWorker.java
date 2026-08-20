package com.procel.telemetry.service.canonical;

import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.observability.CorrelationId;
import com.procel.telemetry.observability.TelemetryObservabilityMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
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
    private final TelemetryObservabilityMetrics metrics;

    public CanonicalTelemetryWorker(
            RawTelemetryClaimService claimService,
            SensorIntegrationSnapshotClient snapshotClient,
            SensorIntegrationProfileSelector profileSelector,
            CanonicalIngestClient ingestClient,
            TelemetryProperties properties,
            TelemetryObservabilityMetrics metrics
    ) {
        this.claimService = claimService;
        this.snapshotClient = snapshotClient;
        this.profileSelector = profileSelector;
        this.ingestClient = ingestClient;
        this.properties = properties;
        this.metrics = metrics;
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
        Instant startedAt = Instant.now();
        String previousCorrelationId = MDC.get(CorrelationId.MDC_KEY);
        CorrelationId.currentOrCreate();
        MDC.put("application", "procel-telemetry");
        MDC.put("rawTelemetryEventId", String.valueOf(event.getId()));
        CanonicalApiDTOs.ProfileSnapshot profile = null;
        try {
            CanonicalApiDTOs.SnapshotResponse snapshot = snapshotClient.snapshot();
            profile = profileSelector.select(event, snapshot);
            CanonicalApiDTOs.CanonicalIngestResponse response = ingestClient.ingest(event, profile);
            String outcome = applyResponse(event, profile, response);
            recordCanonical(event, outcome, startedAt);
            log.info("application=procel-telemetry event=canonical_event_processed rawTelemetryEventId={} status={} attempts={} durationMs={}",
                    event.getId(), outcome, attempts(event), Duration.between(startedAt, Instant.now()).toMillis());
        } catch (ProfileSelectionException ex) {
            claimService.markFailed(event, ex.getCode());
            recordCanonical(event, "failed", startedAt);
            log.info("application=procel-telemetry event=canonical_event_processed rawTelemetryEventId={} status=failed attempts={} durationMs={}",
                    event.getId(), attempts(event), Duration.between(startedAt, Instant.now()).toMillis());
        } catch (TransientCanonicalException ex) {
            claimService.retryOrFail(event, ex.getMessage(), now);
            recordCanonical(event, "retry", startedAt);
            log.info("application=procel-telemetry event=canonical_event_processed rawTelemetryEventId={} status=retry attempts={} durationMs={}",
                    event.getId(), attempts(event), Duration.between(startedAt, Instant.now()).toMillis());
        } catch (CanonicalHttpException ex) {
            claimService.markFailed(event, profile, ex.getErrorCode());
            recordCanonical(event, "failed", startedAt);
            log.info("application=procel-telemetry event=canonical_event_processed rawTelemetryEventId={} status=failed attempts={} durationMs={}",
                    event.getId(), attempts(event), Duration.between(startedAt, Instant.now()).toMillis());
        } catch (RuntimeException ex) {
            claimService.retryOrFail(event, "WORKER_UNEXPECTED_ERROR", now);
            recordCanonical(event, "retry", startedAt);
            log.warn("application=procel-telemetry event=canonical_event_processed rawTelemetryEventId={} status=retry attempts={} durationMs={} exception={}",
                    event.getId(), attempts(event), Duration.between(startedAt, Instant.now()).toMillis(), ex.getClass().getSimpleName());
        } finally {
            restoreCorrelationId(previousCorrelationId);
            MDC.remove("application");
            MDC.remove("rawTelemetryEventId");
        }
    }

    private String applyResponse(
            RawTelemetryEvent event,
            CanonicalApiDTOs.ProfileSnapshot profile,
            CanonicalApiDTOs.CanonicalIngestResponse response
    ) {
        if ("MEASUREMENT_INGESTED".equals(response.code()) && response.medicaoId() != null) {
            claimService.markAccepted(event, profile, response.medicaoId());
            return "accepted";
        }
        if ("DUPLICATE_MESSAGE".equals(response.code())) {
            claimService.markDuplicate(event, profile, response.medicaoId());
            return "duplicate";
        }
        if ("IDEMPOTENCY_CONFLICT".equals(response.code())) {
            claimService.markConflict(event, profile, response.medicaoId(), response.code());
            return "conflict";
        }
        claimService.markFailed(event, profile, response.code() != null ? response.code() : "CANONICAL_RESPONSE_UNHANDLED");
        return "failed";
    }

    private void recordCanonical(RawTelemetryEvent event, String outcome, Instant startedAt) {
        metrics.canonical(event.getSource() != null ? event.getSource().name() : null,
                outcome,
                Duration.between(startedAt, Instant.now()));
    }

    private static int attempts(RawTelemetryEvent event) {
        return event.getProcessing() == null ? 0 : event.getProcessing().getAttempts();
    }

    private static void restoreCorrelationId(String previousCorrelationId) {
        if (previousCorrelationId == null) {
            MDC.remove(CorrelationId.MDC_KEY);
            return;
        }
        MDC.put(CorrelationId.MDC_KEY, previousCorrelationId);
    }
}
