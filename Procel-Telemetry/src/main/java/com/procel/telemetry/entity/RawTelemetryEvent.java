package com.procel.telemetry.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("raw_telemetry_events")
@CompoundIndexes({
        @CompoundIndex(
                name = "ux_raw_telemetry_idempotency",
                def = "{'producerId': 1, 'source': 1, 'messageId': 1}",
                unique = true
        ),
        @CompoundIndex(name = "idx_raw_telemetry_sensor_received", def = "{'sensorId': 1, 'receivedAt': -1}"),
        @CompoundIndex(name = "idx_raw_telemetry_status_received", def = "{'status': 1, 'receivedAt': -1}")
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
    @Indexed(name = "ttl_raw_telemetry_expires_at", expireAfter = "0s")
    private Instant expiresAt;

    public RawTelemetryEvent() {}

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

    public String getId() { return id; }
    public String getProducerId() { return producerId; }
    public TelemetrySource getSource() { return source; }
    public String getMessageId() { return messageId; }
    public String getSensorId() { return sensorId; }
    public Instant getSourceTimestamp() { return sourceTimestamp; }
    public Instant getReceivedAt() { return receivedAt; }
    public Object getPayload() { return payload; }
    public String getPayloadHash() { return payloadHash; }
    public RawTelemetryStatus getStatus() { return status; }
    public Processing getProcessing() { return processing; }
    public Instant getExpiresAt() { return expiresAt; }

    public static class Processing {
        private int attempts;
        private Instant lastAttemptAt;
        private String lastError;
        private String canonicalMeasurementId;
        private String profileId;
        private String parserVersionId;

        public int getAttempts() { return attempts; }
        public Instant getLastAttemptAt() { return lastAttemptAt; }
        public String getLastError() { return lastError; }
        public String getCanonicalMeasurementId() { return canonicalMeasurementId; }
        public String getProfileId() { return profileId; }
        public String getParserVersionId() { return parserVersionId; }
    }
}
