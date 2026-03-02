package com.procel.ingestion.service.sensors;

import java.time.Instant;
import java.util.Map;

public record RawSensorEvent(
        String sensorExternalId,
        Instant timestamp,
        Instant receivedAt,
        String source,
        Map<String, Object> payload
) {}