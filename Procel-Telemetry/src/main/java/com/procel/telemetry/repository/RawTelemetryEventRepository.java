package com.procel.telemetry.repository;

import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.TelemetrySource;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RawTelemetryEventRepository extends MongoRepository<RawTelemetryEvent, String> {
    Optional<RawTelemetryEvent> findByProducerIdAndSourceAndMessageId(
            String producerId,
            TelemetrySource source,
            String messageId
    );
}
