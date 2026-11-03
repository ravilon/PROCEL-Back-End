package com.proben.telemetry.repository;

import com.proben.telemetry.entity.RawTelemetryEvent;
import com.proben.telemetry.entity.RawTelemetryStatus;
import com.proben.telemetry.entity.TelemetrySource;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RawTelemetryEventRepository extends MongoRepository<RawTelemetryEvent, String> {
    long countByStatus(RawTelemetryStatus status);

    Optional<RawTelemetryEvent> findByProducerIdAndSourceAndMessageId(
            String producerId,
            TelemetrySource source,
            String messageId
    );
}
