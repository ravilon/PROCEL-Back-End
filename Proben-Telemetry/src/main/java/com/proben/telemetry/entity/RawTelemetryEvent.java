package com.proben.telemetry.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Raw MQTT or HTTP delivery retained for idempotency, retries, and audit. */
@Document("raw_telemetry_events")
@CompoundIndexes({
        @CompoundIndex(
                name = "ux_raw_telemetry_idempotency",
                def = "{'producerId': 1, 'source': 1, 'messageId': 1}",
                unique = true
        ),
        @CompoundIndex(name = "idx_raw_telemetry_sensor_received", def = "{'sensorId': 1, 'receivedAt': -1}"),
        @CompoundIndex(name = "idx_raw_telemetry_status_received", def = "{'status': 1, 'receivedAt': -1}"),
        @CompoundIndex(name = "idx_raw_telemetry_claim", def = "{'status': 1, 'processing.nextAttemptAt': 1, 'receivedAt': 1}"),
        @CompoundIndex(name = "idx_raw_telemetry_processing_lock", def = "{'status': 1, 'processing.lockedAt': 1}")
})
public class RawTelemetryEvent {
    @Id
    private String id;
    private String producerId;
    private TelemetrySource source;
    private String messageId;
    private String sensorId;
    private Instant sourceTimestamp;
    @Indexed(name = "idx_raw_telemetry_received")
    private Instant receivedAt;
    private Object payload;
    private String payloadHash;
    private RawTelemetryStatus status;
    private Processing processing = new Processing();
    private Reprocessing reprocessing = new Reprocessing();
    private List<ReprocessAuditEntry> reprocessAudit = new ArrayList<>();
    @Indexed(name = "ttl_raw_telemetry_expires_at", expireAfter = "0s")
    private Instant expiresAt;

    /** Creates an empty persistence entity for MongoDB mapping. */
    public RawTelemetryEvent() {}

    /**
     * Creates an event received from a producer.
     * @param producerId producer identity
     * @param source transport source
     * @param messageId producer message identifier
     * @param sensorId external sensor identifier
     * @param sourceTimestamp timestamp supplied by producer
     * @param receivedAt server receipt time
     * @param payload original message body
     * @param payloadHash stable payload fingerprint
     * @param expiresAt retention expiry
     */
    public RawTelemetryEvent(
            String producerId,
            TelemetrySource source,
            String messageId,
            String sensorId,
            Instant sourceTimestamp,
            Instant receivedAt,
            Object payload,
            String payloadHash,
            Instant expiresAt
    ) {
        this.producerId = producerId;
        this.source = source;
        this.messageId = messageId;
        this.sensorId = sensorId;
        this.sourceTimestamp = sourceTimestamp;
        this.receivedAt = receivedAt;
        this.payload = payload;
        this.payloadHash = payloadHash;
        this.expiresAt = expiresAt;
        this.status = RawTelemetryStatus.RECEIVED;
    }

    /** @return MongoDB document identifier */
    public String getId() { return id; }
    /** @return producer identity */
    public String getProducerId() { return producerId; }
    /** @return transport that delivered the event */
    public TelemetrySource getSource() { return source; }
    /** @return producer message identifier */
    public String getMessageId() { return messageId; }
    /** @return external sensor identifier */
    public String getSensorId() { return sensorId; }
    /** @return producer-provided timestamp */
    public Instant getSourceTimestamp() { return sourceTimestamp; }
    /** @return server receipt timestamp */
    public Instant getReceivedAt() { return receivedAt; }
    /** @return original unnormalized payload */
    public Object getPayload() { return payload; }
    /** @return fingerprint used for idempotency conflict detection */
    public String getPayloadHash() { return payloadHash; }
    /** @return current processing status */
    public RawTelemetryStatus getStatus() { return status; }
    /** @return canonical-ingest attempt state */
    public Processing getProcessing() { return processing; }
    /** @return reprocessing request state */
    public Reprocessing getReprocessing() { return reprocessing; }
    /** @return audit entries for reprocessing requests */
    public List<ReprocessAuditEntry> getReprocessAudit() { return reprocessAudit; }
    /** @return configured document expiry */
    public Instant getExpiresAt() { return expiresAt; }

    /** Retry, lease, and canonical-ingest metadata for this event. */
    public static class Processing {
        private int attempts;
        private Instant lastAttemptAt;
        private Instant nextAttemptAt;
        private Instant lockedAt;
        private String workerId;
        private String lastError;
        private String canonicalMeasurementId;
        private String profileId;
        private String parserVersionId;

        public int getAttempts() { return attempts; }
        public Instant getLastAttemptAt() { return lastAttemptAt; }
        public Instant getNextAttemptAt() { return nextAttemptAt; }
        public Instant getLockedAt() { return lockedAt; }
        public String getWorkerId() { return workerId; }
        public String getLastError() { return lastError; }
        public String getCanonicalMeasurementId() { return canonicalMeasurementId; }
        public String getProfileId() { return profileId; }
        public String getParserVersionId() { return parserVersionId; }
    }

    /** Tracks administrative requests to replay a raw event. */
    public static class Reprocessing {
        private int count;
        private Instant lastRequestedAt;
        private String lastRequestedBy;
        private String lastReason;

        public int getCount() { return count; }
        public Instant getLastRequestedAt() { return lastRequestedAt; }
        public String getLastRequestedBy() { return lastRequestedBy; }
        public String getLastReason() { return lastReason; }
    }

    /**
     * Immutable audit snapshot captured when a replay is requested.
     * @param previousStatus status before replay
     * @param lastError previous failure detail
     * @param attempts previous processing attempts
     * @param canonicalMeasurementId linked canonical measurement
     * @param profileId selected profile
     * @param parserVersionId selected parser version
     * @param requestedBy administrator identity
     * @param requestedAt replay request time
     * @param reason operator-provided reason
     */
    public record ReprocessAuditEntry(
            RawTelemetryStatus previousStatus,
            String lastError,
            int attempts,
            String canonicalMeasurementId,
            String profileId,
            String parserVersionId,
            String requestedBy,
            Instant requestedAt,
            String reason
    ) {}
}
