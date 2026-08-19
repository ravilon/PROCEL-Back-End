package com.procel.telemetry.service;

import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.exception.ApiStatusException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
public class TelemetryReprocessService {
    private static final int MAX_REASON_LENGTH = 500;
    private static final Set<RawTelemetryStatus> REPROCESSABLE_STATUSES = EnumSet.of(
            RawTelemetryStatus.CANONICAL_FAILED,
            RawTelemetryStatus.CANONICAL_CONFLICT,
            RawTelemetryStatus.DISCARDED
    );

    private final MongoTemplate mongoTemplate;

    public TelemetryReprocessService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public TelemetryEventDTOs.ReprocessResponse reprocess(
            String id,
            TelemetryEventDTOs.ReprocessRequest request,
            String requestedBy
    ) {
        String reason = normalizeReason(request);
        Instant now = Instant.now();

        RawTelemetryEvent current = mongoTemplate.findById(id, RawTelemetryEvent.class);
        if (current == null) {
            throw new ApiStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Telemetry event not found.");
        }
        RawTelemetryStatus previousStatus = current.getStatus();
        if (!REPROCESSABLE_STATUSES.contains(previousStatus)) {
            throw new ApiStatusException(
                    HttpStatus.CONFLICT,
                    "TELEMETRY_REPROCESS_NOT_ALLOWED",
                    "Telemetry event status cannot be reprocessed."
            );
        }

        RawTelemetryEvent.Processing processing = current.getProcessing() == null
                ? new RawTelemetryEvent.Processing()
                : current.getProcessing();
        RawTelemetryEvent.ReprocessAuditEntry auditEntry = new RawTelemetryEvent.ReprocessAuditEntry(
                previousStatus,
                processing.getLastError(),
                processing.getAttempts(),
                processing.getCanonicalMeasurementId(),
                processing.getProfileId(),
                processing.getParserVersionId(),
                requestedBy,
                now,
                reason
        );

        Query query = new Query()
                .addCriteria(Criteria.where("_id").is(id))
                .addCriteria(Criteria.where("status").in(REPROCESSABLE_STATUSES));
        Update update = new Update()
                .set("status", RawTelemetryStatus.RECEIVED)
                .set("processing.attempts", 0)
                .unset("processing.lastError")
                .unset("processing.lastAttemptAt")
                .unset("processing.nextAttemptAt")
                .unset("processing.lockedAt")
                .unset("processing.workerId")
                .unset("processing.canonicalMeasurementId")
                .unset("processing.profileId")
                .unset("processing.parserVersionId")
                .inc("reprocessing.count", 1)
                .set("reprocessing.lastRequestedAt", now)
                .set("reprocessing.lastRequestedBy", requestedBy)
                .set("reprocessing.lastReason", reason)
                .push("reprocessAudit", auditEntry);

        RawTelemetryEvent updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                RawTelemetryEvent.class
        );
        if (updated == null) {
            throw new ApiStatusException(
                    HttpStatus.CONFLICT,
                    "TELEMETRY_REPROCESS_CONFLICT",
                    "Telemetry event changed before it could be reprocessed."
            );
        }

        int reprocessCount = updated.getReprocessing() == null ? 0 : updated.getReprocessing().getCount();
        return new TelemetryEventDTOs.ReprocessResponse(
                updated.getId(),
                updated.getStatus(),
                previousStatus,
                reprocessCount,
                requestedBy,
                now
        );
    }

    private static String normalizeReason(TelemetryEventDTOs.ReprocessRequest request) {
        String reason = request == null ? null : request.reason();
        if (reason == null || reason.trim().isEmpty()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "reason is required");
        }
        reason = reason.trim();
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "reason must be at most 500 characters");
        }
        return reason;
    }
}
