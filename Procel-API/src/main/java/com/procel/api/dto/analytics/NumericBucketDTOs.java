package com.procel.api.dto.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NumericBucketDTOs {
    private NumericBucketDTOs() {
    }

    public record NumericBucketPage(
            List<NumericBucketResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record NumericBucketResponse(
            String sensorExternalId,
            String sensorNome,
            UUID parametroDefId,
            String parametroNome,
            String unidade,
            String compartimentoId,
            Instant bucketStart,
            Instant bucketEnd,
            BigDecimal averageValue,
            BigDecimal minimumValue,
            BigDecimal maximumValue,
            long sampleCount,
            int aggregationVersion
    ) {
    }

    public record NumericBucketSummaryResponse(
            String sensorExternalId,
            String sensorNome,
            UUID parametroDefId,
            String parametroNome,
            String unidade,
            String compartimentoId,
            Instant from,
            Instant to,
            BigDecimal averageValue,
            BigDecimal minimumValue,
            BigDecimal maximumValue,
            long sampleCount,
            int aggregationVersion,
            long bucketCount
    ) {
    }
}
