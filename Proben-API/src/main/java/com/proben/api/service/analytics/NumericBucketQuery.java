package com.proben.api.service.analytics;

import java.time.Instant;
import java.util.UUID;

/**
 * Componente de numeric bucket query usado pelo modulo API.
 */

public record NumericBucketQuery(
        Instant from,
        Instant to,
        String sensorExternalId,
        String sensorTypeName,
        UUID parametroDefId,
        String compartimentoId,
        Integer aggregationVersion,
        Integer page,
        Integer size
) {
}
