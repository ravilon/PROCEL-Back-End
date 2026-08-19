package com.procel.telemetry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.TelemetrySource;
import com.procel.telemetry.exception.ApiStatusException;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TelemetryIngestService {
    private static final String IDEMPOTENCY_INDEX = "ux_raw_telemetry_idempotency";

    private final RawTelemetryEventRepository repository;
    private final PayloadHashService payloadHashService;
    private final TelemetryProperties properties;
    private final ObjectMapper objectMapper;

    public TelemetryIngestService(
            RawTelemetryEventRepository repository,
            PayloadHashService payloadHashService,
            TelemetryProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.payloadHashService = payloadHashService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TelemetryEventDTOs.IngestResponse ingest(String producerId, JsonNode request) {
        if (producerId == null || producerId.isBlank()) {
            throw new ApiStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token ausente ou invalido");
        }
        if (request == null || !request.isObject()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "body must be a JSON object");
        }

        TelemetrySource source = source(request);
        String messageId = requiredText(request, "messageId");
        String sensorId = optionalText(request, "sensorId");
        Instant sourceTimestamp = optionalInstant(request, "sourceTimestamp");
        JsonNode payload = requiredPayload(request);

        var sensorPresence = request.has("sensorId")
                ? PayloadHashService.PresenceValue.present(request.get("sensorId"))
                : PayloadHashService.PresenceValue.absent();
        var sourceTimestampPresence = request.has("sourceTimestamp")
                ? PayloadHashService.PresenceValue.present(request.get("sourceTimestamp"))
                : PayloadHashService.PresenceValue.absent();
        String payloadHash = payloadHashService.fingerprint(source, sensorPresence, sourceTimestampPresence, payload);

        Instant receivedAt = Instant.now();
        RawTelemetryEvent event = new RawTelemetryEvent(
                producerId,
                source,
                messageId,
                sensorId,
                sourceTimestamp,
                receivedAt,
                objectMapper.convertValue(payload, Object.class),
                payloadHash,
                receivedAt.plusSeconds(properties.getRetentionDays() * 86_400L)
        );

        try {
            return toIngestResponse(repository.save(event), false);
        } catch (DuplicateKeyException ex) {
            if (!isIdempotencyDuplicate(ex)) throw ex;
            RawTelemetryEvent winner = repository
                    .findByProducerIdAndSourceAndMessageId(producerId, source, messageId)
                    .orElseThrow(() -> ex);
            if (payloadHash.equals(winner.getPayloadHash())) {
                return toIngestResponse(winner, true);
            }
            throw new ApiStatusException(
                    HttpStatus.CONFLICT,
                    "RAW_IDEMPOTENCY_CONFLICT",
                    "A different raw payload was already received for this producer, source and messageId."
            );
        }
    }

    private static boolean isIdempotencyDuplicate(DuplicateKeyException ex) {
        String message = ex.getMessage();
        return message != null && message.contains(IDEMPOTENCY_INDEX);
    }

    private TelemetrySource source(JsonNode request) {
        String value = requiredText(request, "source");
        try {
            return TelemetrySource.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "SOURCE_INVALID", "source is invalid");
        }
    }

    private static String requiredText(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", field + " is required");
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", field + " must be a string");
        }
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    private static Instant optionalInstant(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "TIMESTAMP_INVALID", field + " must be an ISO-8601 instant");
        }
        try {
            return Instant.parse(value.asText());
        } catch (Exception ex) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "TIMESTAMP_INVALID", field + " must be an ISO-8601 instant");
        }
    }

    private static JsonNode requiredPayload(JsonNode request) {
        if (!request.has("payload")) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "payload is required");
        }
        return request.get("payload");
    }

    private static TelemetryEventDTOs.IngestResponse toIngestResponse(RawTelemetryEvent event, boolean duplicate) {
        return new TelemetryEventDTOs.IngestResponse(
                event.getId(),
                event.getStatus(),
                duplicate,
                event.getReceivedAt()
        );
    }
}
