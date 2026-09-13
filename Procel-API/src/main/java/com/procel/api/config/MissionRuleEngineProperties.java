package com.procel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "procel.missions")
public record MissionRuleEngineProperties(
        RuleEngineMode ruleEngine,
        int droolsMaxFacts,
        long droolsMaxSampleGapSeconds
) {
    public MissionRuleEngineProperties {
        ruleEngine = ruleEngine == null ? RuleEngineMode.SIMPLE : ruleEngine;
        droolsMaxFacts = droolsMaxFacts <= 0 ? 1_000 : droolsMaxFacts;
        droolsMaxSampleGapSeconds = droolsMaxSampleGapSeconds <= 0 ? 600 : droolsMaxSampleGapSeconds;
    }

    public enum RuleEngineMode {
        SIMPLE,
        DROOLS
    }
}
