package com.proben.api.service.analytics;

/**
 * Componente de aggregation window processor usado pelo modulo API.
 */

public interface AggregationWindowProcessor {
    void process(AggregationWindowWork work);
}
