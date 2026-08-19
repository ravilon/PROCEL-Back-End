package com.procel.api.service.analytics;

import java.time.Instant;
import java.util.UUID;

public record NumericBucketQuery(
        Instant from,
        Instant to,
        String sensorExternalId,
        UUID parametroDefId,
        String compartimentoId,
        Integer aggregationVersion,
        Integer page,
        Integer size
) {
}
