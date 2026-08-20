package com.procel.telemetry.repository;

import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.entity.TelemetrySource;
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
