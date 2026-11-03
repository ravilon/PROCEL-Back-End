package com.proben.api.service.missions.rules.drools;

public record DroolsCacheStats(
        long hits,
        long misses,
        long compilations,
        long evictions,
        int size
) {
}
