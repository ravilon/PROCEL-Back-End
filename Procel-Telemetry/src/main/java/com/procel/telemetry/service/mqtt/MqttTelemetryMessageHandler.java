package com.procel.telemetry.service.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.exception.ApiStatusException;
import com.procel.telemetry.service.TelemetryIngestService;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class MqttTelemetryMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryMessageHandler.class);

    private final TelemetryProperties properties;
    private final MqttTopicParser topicParser;
    private final MqttPayloadAdapter payloadAdapter;
    private final TelemetryIngestService ingestService;
    private final Counters counters = new Counters();

    public MqttTelemetryMessageHandler(
            TelemetryProperties properties,
            MqttTopicParser topicParser,
            MqttPayloadAdapter payloadAdapter,
            TelemetryIngestService ingestService
    ) {
        this.properties = properties;
        this.topicParser = topicParser;
        this.payloadAdapter = payloadAdapter;
        this.ingestService = ingestService;
    }

    public HandlingDecision handle(String topic, MqttMessage message) {
        counters.received.incrementAndGet();
        if (message.isRetained() && properties.getMqtt().isRejectRetained()) {
            counters.discarded.incrementAndGet();
            log.info("discarded retained MQTT telemetry event: topic={}, code=MQTT_RETAINED_REJECTED", topic);
            return HandlingDecision.ACK;
        }
        byte[] payload = message.getPayload();
        if (payload.length > properties.getMaxPayloadBytes()) {
            counters.discarded.incrementAndGet();
            log.info("discarded oversized MQTT telemetry event: topic={}, payloadBytes={}, code=PAYLOAD_TOO_LARGE",
                    topic, payload.length);
            return HandlingDecision.ACK;
        }

        MqttTopicParser.TopicContext topicContext;
        JsonNode request;
        try {
            topicContext = topicParser.parse(topic);
            request = payloadAdapter.toTelemetryRequest(payload, topicContext);
        } catch (MqttTelemetryPermanentException ex) {
            counters.discarded.incrementAndGet();
            log.info("discarded invalid MQTT telemetry event: topic={}, code={}", topic, ex.getCode());
            return HandlingDecision.ACK;
        }

        try {
            TelemetryEventDTOs.IngestResponse response = ingestService.ingest(topicContext.producerId(), request);
            if (response.duplicate()) {
                counters.duplicates.incrementAndGet();
            } else {
                counters.persisted.incrementAndGet();
            }
            log.info("stored MQTT telemetry event: topic={}, producerId={}, sensorId={}, messageId={}, duplicate={}, rawEventId={}",
                    topic, topicContext.producerId(), topicContext.sensorId(), request.get("messageId").asText(),
                    response.duplicate(), response.id());
            return HandlingDecision.ACK;
        } catch (ApiStatusException ex) {
            if ("RAW_IDEMPOTENCY_CONFLICT".equals(ex.getError())) {
                counters.conflicts.incrementAndGet();
            } else {
                counters.discarded.incrementAndGet();
            }
            log.info("discarded permanent MQTT telemetry event: topic={}, producerId={}, sensorId={}, messageId={}, code={}",
                    topic, topicContext.producerId(), topicContext.sensorId(), request.get("messageId").asText(), ex.getError());
            return HandlingDecision.ACK;
        } catch (TransientDataAccessException | DataAccessResourceFailureException ex) {
            counters.transientFailures.incrementAndGet();
            log.warn("transient MongoDB failure for MQTT telemetry event: topic={}, producerId={}, sensorId={}, messageId={}",
                    topic, topicContext.producerId(), topicContext.sensorId(), request.get("messageId").asText());
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
