package com.proben.api.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class ApiObservabilityMetrics {
    private final MeterRegistry registry;

    public ApiObservabilityMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void aggregationJobCreated(String outcome) {
        counter("proben.analytics.aggregation.jobs", "outcome", outcome).increment();
    }

    public void windowProcessed(String outcome, int attempts, Duration duration) {
        counter("proben.analytics.aggregation.windows.processed", "outcome", outcome).increment();
        if ("completed".equals(outcome)) {
            counter("proben.analytics.aggregation.windows.completed", "outcome", outcome).increment();
        }
        if ("failed".equals(outcome)) {
            counter("proben.analytics.aggregation.windows.failed", "outcome", outcome).increment();
        }
        if ("retry".equals(outcome) || attempts > 1) {
            counter("proben.analytics.aggregation.windows.retries", "outcome", outcome).increment();
        }
        timer("proben.analytics.aggregation.windows.duration", "outcome", outcome).record(duration);
    }

    public void bucketsPersisted(long count) {
        DistributionSummary.builder("proben.analytics.buckets.persisted")
                .description("Numeric aggregation buckets inserted or updated")
                .register(registry)
                .record(Math.max(0, count));
    }

    public void analyticsQuery(String type, String outcome, Duration duration) {
        counter("proben.analytics.queries", "type", type, "outcome", outcome).increment();
        if ("error".equals(outcome)) {
            counter("proben.analytics.query.errors", "type", type, "outcome", outcome).increment();
        }
        timer("proben.analytics.query.duration", "type", type, "outcome", outcome).record(duration);
    }

    public void missionEvaluationProcessed(String outcome, int attempts, Duration duration) {
        counter("proben.missions.evaluations", "outcome", outcome).increment();
        switch (outcome) {
            case "completed" -> counter("proben.missions.evaluations.completed", "outcome", outcome).increment();
            case "ignored" -> counter("proben.missions.evaluations.ignored", "outcome", outcome).increment();
            case "retry" -> counter("proben.missions.evaluations.retried", "outcome", outcome).increment();
            case "failed" -> counter("proben.missions.evaluations.failed", "outcome", outcome).increment();
            default -> { }
        }
        if (attempts > 1 && !"retry".equals(outcome)) {
            counter("proben.missions.evaluations.retried", "outcome", outcome).increment();
        }
        timer("proben.missions.evaluation.duration", "outcome", outcome).record(duration);
    }

    public void missionEventDetected() {
        counter("proben.missions.events.detected").increment();
    }

    public void missionActivityCreated() {
        counter("proben.missions.activities.created").increment();
    }

    public void missionActivityProgressed() {
        counter("proben.missions.activities.progressed").increment();
    }

    public void missionActivityCompleted() {
        counter("proben.missions.activities.completed").increment();
    }

    public void missionBeneficiariesResolved(int count) {
        counter("proben.missions.beneficiaries.resolved").increment(Math.max(0, count));
    }

    public void missionBeneficiariesEmpty() {
        counter("proben.missions.beneficiaries.empty").increment();
    }

    public void missionActivityProcessingFailure(String type) {
        counter("proben.missions.activity.processing.failures", "type", type == null || type.isBlank() ? "unknown" : type).increment();
    }

    public void missionXpGranted() {
        counter("proben.missions.xp.granted").increment();
    }

    public void missionXpAmount(int amount) {
        DistributionSummary.builder("proben.missions.xp.amount")
                .description("XP amount granted by automatic mission completion")
                .register(registry)
                .record(Math.max(0, amount));
    }

    public void missionXpDuplicate() {
        counter("proben.missions.xp.duplicates").increment();
    }

    public void missionXpFailure(String type) {
        counter("proben.missions.xp.failures", "type", type == null || type.isBlank() ? "unknown" : type).increment();
    }

    public void droolsCompilation(String mode, Duration duration) {
        counter("proben.missions.drools.compilations", "mode", mode).increment();
        timer("proben.missions.drools.compilation.duration", "mode", mode).record(duration);
    }

    public void droolsCompilationFailure(String mode) {
        counter("proben.missions.drools.compilation.failures", "mode", mode).increment();
    }

    public void droolsCacheHit(String mode) {
        counter("proben.missions.drools.cache.hits", "mode", mode).increment();
    }

    public void droolsCacheMiss(String mode) {
        counter("proben.missions.drools.cache.misses", "mode", mode).increment();
    }

    public void droolsCacheEviction(String mode, long count) {
        counter("proben.missions.drools.cache.evictions", "mode", mode).increment(Math.max(0, count));
    }

    public void droolsEvaluation(String mode, String result, Duration duration) {
        counter("proben.missions.drools.evaluations", "mode", mode, "result", result).increment();
        timer("proben.missions.drools.evaluation.duration", "mode", mode, "result", result).record(duration);
    }

    public void droolsEvaluationFailure(String mode) {
        counter("proben.missions.drools.evaluation.failures", "mode", mode, "result", "failed").increment();
    }

    public void droolsFacts(String mode, long count) {
        DistributionSummary.builder("proben.missions.drools.facts")
                .tags("mode", mode)
                .description("Facts evaluated by Drools mission rule engine")
                .register(registry)
                .record(Math.max(0, count));
    }

    public void droolsLimitRejection(String mode, String limit) {
        counter("proben.missions.drools.limit.rejections", "mode", mode, "limit", limit).increment();
    }

    public void registerMissionBacklogGauge(Object owner, Supplier<Number> supplier) {
        Gauge.builder("proben.missions.backlog", owner, ignored -> supplier.get().doubleValue())
                .register(registry);
    }

    public void missionTemporalWindowOpened() {
        counter("proben.missions.temporal.windows.opened").increment();
    }

    public void missionTemporalWindowSatisfied() {
        counter("proben.missions.temporal.windows.satisfied").increment();
    }

    public void missionTemporalWindowExpired() {
        counter("proben.missions.temporal.windows.expired").increment();
    }

    public void missionTemporalWindowFailed() {
        counter("proben.missions.temporal.windows.failed").increment();
    }

    public void missionTemporalWindowRetry() {
        counter("proben.missions.temporal.windows.retries").increment();
    }

    public void registerMissionTemporalWindowBacklogGauge(Object owner, Supplier<Number> supplier) {
        Gauge.builder("proben.missions.temporal.windows.backlog", owner, ignored -> supplier.get().doubleValue())
                .register(registry);
    }

    public void environmentRuleProcessed(String executionType, String result, Duration duration) {
        String type = executionType == null ? "unknown" : executionType.toLowerCase();
        String outcome = result == null ? "unknown" : result.toLowerCase();
        counter("proben.environment.rules.executions", "type", type, "result", outcome).increment();
        timer("proben.environment.rules.duration", "type", type, "result", outcome).record(duration);
    }

    public void environmentRuleRetry(boolean terminal) {
        counter("proben.environment.rules.retries", "terminal", Boolean.toString(terminal)).increment();
    }

    public void environmentRuleLeaseRecovered() {
        counter("proben.environment.rules.leases.recovered").increment();
    }

    public void environmentRuleDuplicate() {
        counter("proben.environment.rules.duplicates.prevented").increment();
    }

    public void environmentRulePoints(long amount) {
        DistributionSummary.builder("proben.environment.rules.points")
                .description("XP granted by environment rules")
                .register(registry)
                .record(Math.max(0, amount));
    }

    public void registerEnvironmentRuleBacklogGauge(Object owner, Supplier<Number> supplier) {
        Gauge.builder("proben.environment.rules.backlog", owner, ignored -> supplier.get().doubleValue())
                .register(registry);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }
}
