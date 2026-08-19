package com.procel.telemetry.service.canonical;

import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.RawTelemetryStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class RawTelemetryClaimService {
    private final MongoTemplate mongoTemplate;
    private final TelemetryProperties properties;
    private final BackoffPolicy backoffPolicy;

    public RawTelemetryClaimService(
            MongoTemplate mongoTemplate,
            TelemetryProperties properties,
            BackoffPolicy backoffPolicy
    ) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.backoffPolicy = backoffPolicy;
    }

    public RawTelemetryEvent claimNext(String workerId, Instant now) {
        Query query = new Query()
                .addCriteria(Criteria.where("status").is(RawTelemetryStatus.RECEIVED))
                .addCriteria(new Criteria().orOperator(
                        Criteria.where("processing.nextAttemptAt").exists(false),
                        Criteria.where("processing.nextAttemptAt").lte(now)
                ))
                .with(Sort.by(Sort.Direction.ASC, "receivedAt").and(Sort.by(Sort.Direction.ASC, "id")))
                .limit(1);
        Update update = new Update()
                .set("status", RawTelemetryStatus.PROCESSING)
                .inc("processing.attempts", 1)
                .set("processing.lastAttemptAt", now)
                .set("processing.lockedAt", now)
                .set("processing.workerId", workerId)
                .unset("processing.nextAttemptAt")
                .unset("processing.lastError");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                RawTelemetryEvent.class
        );
    }

    public void markAccepted(RawTelemetryEvent event, CanonicalApiDTOs.ProfileSnapshot profile, String canonicalMeasurementId) {
        markTerminal(event, profile, RawTelemetryStatus.CANONICAL_ACCEPTED, canonicalMeasurementId, null);
    }

    public void markDuplicate(RawTelemetryEvent event, CanonicalApiDTOs.ProfileSnapshot profile, String canonicalMeasurementId) {
        markTerminal(event, profile, RawTelemetryStatus.CANONICAL_DUPLICATE, canonicalMeasurementId, null);
    }

    public void markConflict(RawTelemetryEvent event, CanonicalApiDTOs.ProfileSnapshot profile, String canonicalMeasurementId, String error) {
        markTerminal(event, profile, RawTelemetryStatus.CANONICAL_CONFLICT, canonicalMeasurementId, error);
    }

    public void markFailed(RawTelemetryEvent event, String error) {
        markTerminal(event, null, RawTelemetryStatus.CANONICAL_FAILED, null, error);
    }

    public void markFailed(RawTelemetryEvent event, CanonicalApiDTOs.ProfileSnapshot profile, String error) {
        markTerminal(event, profile, RawTelemetryStatus.CANONICAL_FAILED, null, error);
    }

    public void retryOrFail(RawTelemetryEvent event, String error, Instant now) {
        int attempts = event.getProcessing().getAttempts();
        if (attempts >= properties.getCanonicalWorker().getMaxAttempts()) {
            markTerminal(event, null, RawTelemetryStatus.CANONICAL_FAILED, null, error);
            return;
        }
        Duration delay = backoffPolicy.delayForAttempt(attempts);
        updateProcessing(event, new Update()
                .set("status", RawTelemetryStatus.RECEIVED)
                .set("processing.nextAttemptAt", now.plus(delay))
                .set("processing.lastError", error)
                .unset("processing.lockedAt")
                .unset("processing.workerId"));
    }

    public long recoverStuck(String workerId, Instant now) {
        Instant cutoff = now.minus(properties.getCanonicalWorker().getLeaseTimeout());
        Query retryQuery = new Query()
                .addCriteria(Criteria.where("status").is(RawTelemetryStatus.PROCESSING))
                .addCriteria(Criteria.where("processing.lockedAt").lt(cutoff))
                .addCriteria(new Criteria().orOperator(
                        Criteria.where("processing.attempts").lt(properties.getCanonicalWorker().getMaxAttempts()),
                        Criteria.where("processing.attempts").exists(false)
                ));
        Update retry = new Update()
                .set("status", RawTelemetryStatus.RECEIVED)
                .set("processing.nextAttemptAt", now)
                .set("processing.lastError", "PROCESSING_LEASE_EXPIRED")
                .unset("processing.lockedAt")
                .unset("processing.workerId");
        long retried = mongoTemplate.updateMulti(retryQuery, retry, RawTelemetryEvent.class).getModifiedCount();

        Query failQuery = new Query()
                .addCriteria(Criteria.where("status").is(RawTelemetryStatus.PROCESSING))
                .addCriteria(Criteria.where("processing.lockedAt").lt(cutoff))
                .addCriteria(Criteria.where("processing.attempts").gte(properties.getCanonicalWorker().getMaxAttempts()));
        Update fail = new Update()
                .set("status", RawTelemetryStatus.CANONICAL_FAILED)
                .set("processing.lastError", "PROCESSING_LEASE_EXPIRED")
                .unset("processing.nextAttemptAt")
                .unset("processing.lockedAt")
                .unset("processing.workerId");
        long failed = mongoTemplate.updateMulti(failQuery, fail, RawTelemetryEvent.class).getModifiedCount();
        return retried + failed;
    }

    private void markTerminal(
            RawTelemetryEvent event,
            CanonicalApiDTOs.ProfileSnapshot profile,
            RawTelemetryStatus status,
            String canonicalMeasurementId,
            String error
    ) {
        Update update = new Update()
                .set("status", status)
                .unset("processing.nextAttemptAt")
                .unset("processing.lockedAt")
                .unset("processing.workerId");
        if (error == null || error.isBlank()) {
            update.unset("processing.lastError");
        } else {
            update.set("processing.lastError", error);
        }
        if (canonicalMeasurementId != null && !canonicalMeasurementId.isBlank()) {
            update.set("processing.canonicalMeasurementId", canonicalMeasurementId);
        }
        if (profile != null) {
            update.set("processing.profileId", profile.id());
            update.set("processing.parserVersionId", profile.activeParserVersion().id());
        }
        updateProcessing(event, update);
    }

    private void updateProcessing(RawTelemetryEvent event, Update update) {
        Query query = new Query()
                .addCriteria(Criteria.where("_id").is(event.getId()))
                .addCriteria(Criteria.where("status").is(RawTelemetryStatus.PROCESSING))
                .addCriteria(Criteria.where("processing.workerId").is(event.getProcessing().getWorkerId()));
        mongoTemplate.updateFirst(query, update, RawTelemetryEvent.class);
    }
}
