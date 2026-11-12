package com.proben.api.service.sensors;

import java.time.Instant;
import java.util.Map;

/**
 * Componente de raw sensor event usado pelo modulo API.
 */

public record RawSensorEvent(
        String sensorExternalId,
        Instant timestamp,
        Instant receivedAt,
        String source,
        Map<String, Object> payload
) {}