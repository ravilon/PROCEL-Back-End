package com.procel.api.service.missions.rules.drools;

import com.procel.api.config.MissionRuleEngineProperties;

import java.time.Duration;
import java.util.Objects;

public record DroolsRuleEngineSettings(
        int maxFactsPerEvaluation,
        int maxCacheEntries,
        Duration cacheExpiration,
        Duration compilationTimeout,
        Duration evaluationTimeout,
        Duration maximumSampleGap,
        Duration maximumEvaluationSpan
) {
    public DroolsRuleEngineSettings {
        if (maxFactsPerEvaluation <= 0) throw new IllegalArgumentException("maxFactsPerEvaluation must be positive");
        if (maxCacheEntries <= 0) throw new IllegalArgumentException("maxCacheEntries must be positive");
        requirePositive(cacheExpiration, "cacheExpiration");
        requirePositive(compilationTimeout, "compilationTimeout");
        requirePositive(evaluationTimeout, "evaluationTimeout");
        requirePositive(maximumSampleGap, "maximumSampleGap");
        requirePositive(maximumEvaluationSpan, "maximumEvaluationSpan");
    }

    public static DroolsRuleEngineSettings defaults() {
        return new DroolsRuleEngineSettings(
                5_000,
                200,
                Duration.ofMinutes(30),
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                Duration.ofHours(24)
        );
    }

    public static DroolsRuleEngineSettings from(MissionRuleEngineProperties.DroolsProperties properties) {
        return new DroolsRuleEngineSettings(
                properties.maxFactsPerEvaluation(),
                properties.maxCacheEntries(),
                properties.cacheExpiration(),
                properties.compilationTimeout(),
                properties.evaluationTimeout(),
                properties.maximumSampleGap(),
                properties.maximumEvaluationSpan()
        );
    }

    String fingerprintMaterial() {
        return "maxSampleGap=" + maximumSampleGap
                + "|maximumEvaluationSpan=" + maximumEvaluationSpan;
    }

    private static void requirePositive(Duration duration, String field) {
        Objects.requireNonNull(duration, field + " is required");
        if (!duration.isPositive()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
