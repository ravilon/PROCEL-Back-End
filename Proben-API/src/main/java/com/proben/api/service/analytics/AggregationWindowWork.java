package com.proben.api.service.analytics;

import java.time.Instant;
import java.util.UUID;

/**
 * Componente de aggregation window work usado pelo modulo API.
 */

public record AggregationWindowWork(
        UUID windowId,
        UUID jobId,
        int index,
        Instant from,
        Instant to,
        String sensorExternalId,
        String compartimentoId,
        int attempts
) {
}
