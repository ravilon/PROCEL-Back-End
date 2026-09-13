package com.procel.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Validated
@ConfigurationProperties(prefix = "procel.missions")
public record MissionRuleEngineProperties(
        RuleEngineMode ruleEngine,
        @Valid DroolsProperties drools
) {
    public MissionRuleEngineProperties {
        ruleEngine = ruleEngine == null ? RuleEngineMode.SIMPLE : ruleEngine;
        drools = drools == null ? DroolsProperties.defaults() : drools.withDefaults();
    }

    public enum RuleEngineMode {
        SIMPLE,
        DROOLS
    }

    public record DroolsProperties(
            @Min(1) Integer maxFactsPerEvaluation,
            @Min(1) Integer maxCacheEntries,
            @NotNull @DurationUnit(ChronoUnit.SECONDS) Duration cacheExpiration,
            @NotNull @DurationUnit(ChronoUnit.SECONDS) Duration compilationTimeout,
            @NotNull @DurationUnit(ChronoUnit.SECONDS) Duration evaluationTimeout,
            @NotNull @DurationUnit(ChronoUnit.SECONDS) Duration maximumSampleGap,
            @NotNull @DurationUnit(ChronoUnit.SECONDS) Duration maximumEvaluationSpan
    ) {
        private static final int DEFAULT_MAX_FACTS = 5_000;
        private static final int DEFAULT_MAX_CACHE_ENTRIES = 200;
        private static final Duration DEFAULT_CACHE_EXPIRATION = Duration.ofMinutes(30);
        private static final Duration DEFAULT_COMPILATION_TIMEOUT = Duration.ofSeconds(10);
        private static final Duration DEFAULT_EVALUATION_TIMEOUT = Duration.ofSeconds(5);
        private static final Duration DEFAULT_MAXIMUM_SAMPLE_GAP = Duration.ofMinutes(5);
        private static final Duration DEFAULT_MAXIMUM_EVALUATION_SPAN = Duration.ofHours(24);

        public DroolsProperties {
            requirePositiveIfPresent(cacheExpiration, "cacheExpiration");
            requirePositiveIfPresent(compilationTimeout, "compilationTimeout");
            requirePositiveIfPresent(evaluationTimeout, "evaluationTimeout");
            requirePositiveIfPresent(maximumSampleGap, "maximumSampleGap");
            requirePositiveIfPresent(maximumEvaluationSpan, "maximumEvaluationSpan");
        }

        static DroolsProperties defaults() {
            return new DroolsProperties(
                    DEFAULT_MAX_FACTS,
                    DEFAULT_MAX_CACHE_ENTRIES,
                    DEFAULT_CACHE_EXPIRATION,
                    DEFAULT_COMPILATION_TIMEOUT,
                    DEFAULT_EVALUATION_TIMEOUT,
                    DEFAULT_MAXIMUM_SAMPLE_GAP,
                    DEFAULT_MAXIMUM_EVALUATION_SPAN
            );
        }

        DroolsProperties withDefaults() {
            return new DroolsProperties(
                    maxFactsPerEvaluation == null ? DEFAULT_MAX_FACTS : maxFactsPerEvaluation,
                    maxCacheEntries == null ? DEFAULT_MAX_CACHE_ENTRIES : maxCacheEntries,
                    cacheExpiration == null ? DEFAULT_CACHE_EXPIRATION : cacheExpiration,
                    compilationTimeout == null ? DEFAULT_COMPILATION_TIMEOUT : compilationTimeout,
                    evaluationTimeout == null ? DEFAULT_EVALUATION_TIMEOUT : evaluationTimeout,
                    maximumSampleGap == null ? DEFAULT_MAXIMUM_SAMPLE_GAP : maximumSampleGap,
                    maximumEvaluationSpan == null ? DEFAULT_MAXIMUM_EVALUATION_SPAN : maximumEvaluationSpan
            );
        }

        private static void requirePositiveIfPresent(Duration value, String field) {
            if (value != null && !value.isPositive()) {
                throw new IllegalArgumentException("procel.missions.drools." + field + " must be positive");
            }
        }
    }
}
