package com.procel.telemetry.service;

import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.entity.TelemetrySource;
import com.procel.telemetry.exception.ApiStatusException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TelemetryQueryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final MongoTemplate mongoTemplate;

    public TelemetryQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public TelemetryEventDTOs.EventPageResponse list(
            TelemetrySource source,
            RawTelemetryStatus status,
            String sensorId,
            String producerId,
            String messageId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        if (page < 0) throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "page must be >= 0");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "size must be between 1 and 100");
        }

        Query query = query(source, status, sensorId, producerId, messageId, from, to);
        long total = mongoTemplate.count(query, RawTelemetryEvent.class);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "receivedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        List<TelemetryEventDTOs.EventResponse> content = mongoTemplate
                .find(query.with(pageRequest), RawTelemetryEvent.class)
                .stream()
                .map(TelemetryQueryService::toResponse)
                .toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new TelemetryEventDTOs.EventPageResponse(content, page, size, total, totalPages);
    }

    public TelemetryEventDTOs.EventResponse get(String id) {
        RawTelemetryEvent event = mongoTemplate.findById(id, RawTelemetryEvent.class);
        if (event == null) {
            throw new ApiStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Telemetry event not found.");
        }
        return toResponse(event);
    }

    private static Query query(
            TelemetrySource source,
            RawTelemetryStatus status,
            String sensorId,
            String producerId,
            String messageId,
            Instant from,
            Instant to
    ) {
        List<Criteria> criteria = new ArrayList<>();
        if (source != null) criteria.add(Criteria.where("source").is(source));
        if (status != null) criteria.add(Criteria.where("status").is(status));
        if (sensorId != null && !sensorId.isBlank()) criteria.add(Criteria.where("sensorId").is(sensorId.trim()));
        if (producerId != null && !producerId.isBlank()) criteria.add(Criteria.where("producerId").is(producerId.trim()));
        if (messageId != null && !messageId.isBlank()) criteria.add(Criteria.where("messageId").is(messageId.trim()));
        if (from != null || to != null) {
            Criteria received = Criteria.where("receivedAt");
            if (from != null) received = received.gte(from);
            if (to != null) received = received.lte(to);
            criteria.add(received);
        }
        Query query = new Query();
        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria));
        }
        return query;
    }

    private static TelemetryEventDTOs.EventResponse toResponse(RawTelemetryEvent event) {
        return new TelemetryEventDTOs.EventResponse(
                event.getId(),
                event.getProducerId(),
                event.getSource(),
                event.getMessageId(),
                event.getSensorId(),
                event.getSourceTimestamp(),
                event.getReceivedAt(),
                event.getPayload(),
                event.getPayloadHash(),
                event.getStatus(),
                event.getProcessing(),
                event.getReprocessing(),
                event.getReprocessAudit(),
                event.getExpiresAt()
        );
    }
}
