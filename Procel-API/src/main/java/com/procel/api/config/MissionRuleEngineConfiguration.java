package com.procel.api.config;

import com.procel.api.service.missions.rules.MissionRuleEngine;
import com.procel.api.service.missions.rules.drools.DroolsMissionRuleEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MissionRuleEngineConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "procel.missions", name = "rule-engine", havingValue = "drools")
    MissionRuleEngine droolsMissionRuleEngine(MissionRuleEngineProperties properties) {
        return new DroolsMissionRuleEngine(properties.droolsMaxFacts(), properties.droolsMaxSampleGapSeconds());
    }
}
