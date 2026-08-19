package com.procel.api.service.analytics;

import java.time.Instant;
import java.util.UUID;

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
