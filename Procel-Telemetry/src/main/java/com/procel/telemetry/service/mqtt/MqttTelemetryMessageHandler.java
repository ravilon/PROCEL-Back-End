package com.procel.telemetry.service.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.exception.ApiStatusException;
import com.procel.telemetry.observability.TelemetryObservabilityMetrics;
import com.procel.telemetry.service.TelemetryIngestService;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MqttTelemetryMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryMessageHandler.class);

    private final TelemetryProperties properties;
    private final MqttTopicParser topicParser;
    private final MqttPayloadAdapter payloadAdapter;
    private final TelemetryIngestService ingestService;
    private final TelemetryObservabilityMetrics metrics;
    private final Counters counters = new Counters();

    public MqttTelemetryMessageHandler(
            TelemetryProperties properties,
            MqttTopicParser topicParser,
            MqttPayloadAdapter payloadAdapter,
            TelemetryIngestService ingestService,
            TelemetryObservabilityMetrics metrics
    ) {
        this.properties = properties;
        this.topicParser = topicParser;
        this.payloadAdapter = payloadAdapter;
        this.ingestService = ingestService;
        this.metrics = metrics;
    }

    public HandlingDecision handle(String topic, MqttMessage message) {
        Instant startedAt = Instant.now();
        counters.received.incrementAndGet();
        metrics.mqtt("received", Duration.ZERO);
        if (message.isRetained() && properties.getMqtt().isRejectRetained()) {
            counters.discarded.incrementAndGet();
            metrics.mqtt("rejected", Duration.between(startedAt, Instant.now()));
            log.info("application=procel-telemetry event=mqtt_message_rejected status=MQTT_RETAINED_REJECTED durationMs={}",
                    Duration.between(startedAt, Instant.now()).toMillis());
            return HandlingDecision.ACK;
        }
        byte[] payload = message.getPayload();
        if (payload.length > properties.getMaxPayloadBytes()) {
            counters.discarded.incrementAndGet();
            Duration duration = Duration.between(startedAt, Instant.now());
            metrics.mqtt("rejected", duration);
            log.info("application=procel-telemetry event=mqtt_message_rejected status=PAYLOAD_TOO_LARGE payloadBytes={} durationMs={}",
                    payload.length, duration.toMillis());
            return HandlingDecision.ACK;
        }

        MqttTopicParser.TopicContext topicContext;
        JsonNode request;
        try {
            topicContext = topicParser.parse(topic);
            request = payloadAdapter.toTelemetryRequest(payload, topicContext);
        } catch (MqttTelemetryPermanentException ex) {
            counters.discarded.incrementAndGet();
            Duration duration = Duration.between(startedAt, Instant.now());
            metrics.mqtt("rejected", duration);
            log.info("application=procel-telemetry event=mqtt_message_rejected status={} durationMs={}",
                    ex.getCode(), duration.toMillis());
            return HandlingDecision.ACK;
        }

        try {
            TelemetryEventDTOs.IngestResponse response = ingestService.ingest(topicContext.producerId(), request);
            Duration duration = Duration.between(startedAt, Instant.now());
            if (response.duplicate()) {
                counters.duplicates.incrementAndGet();
                metrics.mqtt("duplicate", duration);
            } else {
                counters.persisted.incrementAndGet();
                metrics.mqtt("persisted", duration);
            }
            log.info("application=procel-telemetry event=mqtt_message_stored rawTelemetryEventId={} status={} durationMs={}",
                    response.id(), response.duplicate() ? "duplicate" : "persisted", duration.toMillis());
            return HandlingDecision.ACK;
        } catch (ApiStatusException ex) {
            Duration duration = Duration.between(startedAt, Instant.now());
            if ("RAW_IDEMPOTENCY_CONFLICT".equals(ex.getError())) {
                counters.conflicts.incrementAndGet();
                metrics.mqtt("conflict", duration);
            } else {
                counters.discarded.incrementAndGet();
                metrics.mqtt("rejected", duration);
            }
            log.info("application=procel-telemetry event=mqtt_message_rejected status={} durationMs={}",
                    ex.getError(), duration.toMillis());
            return HandlingDecision.ACK;
        } catch (TransientDataAccessException | DataAccessResourceFailureException ex) {
            counters.transientFailures.incrementAndGet();
            Duration duration = Duration.between(startedAt, Instant.now());
            metrics.mqtt("retry", duration);
            log.warn("application=procel-telemetry event=mqtt_message_retry status=TRANSIENT_DATA_ACCESS durationMs={}",
                    duration.toMillis());
            return HandlingDecision.RETRY;
        }
    }

    public Counters counters() {
        return counters;
    }

    public enum HandlingDecision {
        ACK,
        RETRY
    }

    public static class Counters {
        private final AtomicLong received = new AtomicLong();
        private final AtomicLong persisted = new AtomicLong();
        private final AtomicLong duplicates = new AtomicLong();
        private final AtomicLong conflicts = new AtomicLong();
        private final AtomicLong discarded = new AtomicLong();
        private final AtomicLong transientFailures = new AtomicLong();

        public long received() { return received.get(); }
        public long persisted() { return persisted.get(); }
        public long duplicates() { return duplicates.get(); }
        public long conflicts() { return conflicts.get(); }
        public long discarded() { return discarded.get(); }
        public long transientFailures() { return transientFailures.get(); }
    }
}
