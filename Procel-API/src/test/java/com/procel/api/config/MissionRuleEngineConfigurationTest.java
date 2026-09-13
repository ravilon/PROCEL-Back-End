package com.procel.api.config;

import com.procel.api.service.missions.rules.MissionRuleEngine;
import com.procel.api.service.missions.rules.SimpleMissionRuleEngine;
import com.procel.api.service.missions.rules.drools.DroolsMissionRuleEngine;
import com.procel.api.observability.ApiObservabilityMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class MissionRuleEngineConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void simpleEngineIsDefaultWhenPropertyIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MissionRuleEngine.class);
            assertThat(context.getBean(MissionRuleEngine.class)).isInstanceOf(SimpleMissionRuleEngine.class);
        });
    }

    @Test
    void droolsEngineIsSelectedOnlyWhenConfigured() {
        contextRunner
                .withPropertyValues("procel.missions.rule-engine=drools")
                .run(context -> {
                    assertThat(context).hasBean("droolsMissionRuleEngine");
                    assertThat(context.getBean(MissionRuleEngine.class)).isInstanceOf(DroolsMissionRuleEngine.class);
                });
    }

    @Test
    void invalidEngineConfigurationFailsStartup() {
        contextRunner
                .withPropertyValues("procel.missions.rule-engine=invalid")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidDroolsLimitFailsStartup() {
        contextRunner
                .withPropertyValues("procel.missions.drools.max-facts-per-evaluation=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidDroolsDurationFailsStartup() {
        contextRunner
                .withPropertyValues("procel.missions.drools.cache-expiration=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(MissionRuleEngineProperties.class)
    @Import(MissionRuleEngineConfiguration.class)
    static class TestConfiguration {
        @Bean
        SimpleMissionRuleEngine simpleMissionRuleEngine() {
            return new SimpleMissionRuleEngine();
        }

        @Bean
        ApiObservabilityMetrics apiObservabilityMetrics() {
            return new ApiObservabilityMetrics(new SimpleMeterRegistry());
        }
    }
}
