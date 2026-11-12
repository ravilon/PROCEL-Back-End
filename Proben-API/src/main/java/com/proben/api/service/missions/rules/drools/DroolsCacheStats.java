package com.proben.api.service.missions.rules.drools;

/**
 * Componente de drools cache stats usado pelo modulo API.
 */

public record DroolsCacheStats(
        long hits,
        long misses,
        long compilations,
        long evictions,
        int size
) {
}
