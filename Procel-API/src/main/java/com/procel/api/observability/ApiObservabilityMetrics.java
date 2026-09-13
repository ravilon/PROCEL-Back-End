package com.procel.api.observability;

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
        counter("procel.analytics.aggregation.jobs", "outcome", outcome).increment();
    }

    public void windowProcessed(String outcome, int attempts, Duration duration) {
        counter("procel.analytics.aggregation.windows.processed", "outcome", outcome).increment();
        if ("completed".equals(outcome)) {
            counter("procel.analytics.aggregation.windows.completed", "outcome", outcome).increment();
        }
        if ("failed".equals(outcome)) {
            counter("procel.analytics.aggregation.windows.failed", "outcome", outcome).increment();
        }
        if ("retry".equals(outcome) || attempts > 1) {
            counter("procel.analytics.aggregation.windows.retries", "outcome", outcome).increment();
        }
        timer("procel.analytics.aggregation.windows.duration", "outcome", outcome).record(duration);
    }

    public void bucketsPersisted(long count) {
        DistributionSummary.builder("procel.analytics.buckets.persisted")
                .description("Numeric aggregation buckets inserted or updated")
                .register(registry)
                .record(Math.max(0, count));
    }

    public void analyticsQuery(String type, String outcome, Duration duration) {
        counter("procel.analytics.queries", "type", type, "outcome", outcome).increment();
        if ("error".equals(outcome)) {
            counter("procel.analytics.query.errors", "type", type, "outcome", outcome).increment();
        }
        timer("procel.analytics.query.duration", "type", type, "outcome", outcome).record(duration);
    }

    public void missionEvaluationProcessed(String outcome, int attempts, Duration duration) {
        counter("procel.missions.evaluations", "outcome", outcome).increment();
        switch (outcome) {
            case "completed" -> counter("procel.missions.evaluations.completed", "outcome", outcome).increment();
            case "ignored" -> counter("procel.missions.evaluations.ignored", "outcome", outcome).increment();
            case "retry" -> counter("procel.missions.evaluations.retried", "outcome", outcome).increment();
            case "failed" -> counter("procel.missions.evaluations.failed", "outcome", outcome).increment();
            default -> { }
        }
        if (attempts > 1 && !"retry".equals(outcome)) {
            counter("procel.missions.evaluations.retried", "outcome", outcome).increment();
        }
        timer("procel.missions.evaluation.duration", "outcome", outcome).record(duration);
    }

    public void missionEventDetected() {
        counter("procel.missions.events.detected").increment();
    }

    public void missionActivityCreated() {
        counter("procel.missions.activities.created").increment();
    }

    public void missionActivityProgressed() {
        counter("procel.missions.activities.progressed").increment();
    }

    public void missionActivityCompleted() {
        counter("procel.missions.activities.completed").increment();
    }

    public void missionBeneficiariesResolved(int count) {
        counter("procel.missions.beneficiaries.resolved").increment(Math.max(0, count));
    }

    public void missionBeneficiariesEmpty() {
        counter("procel.missions.beneficiaries.empty").increment();
    }

    public void missionActivityProcessingFailure(String type) {
        counter("procel.missions.activity.processing.failures", "type", type == null || type.isBlank() ? "unknown" : type).increment();
    }

    public void missionXpGranted() {
        counter("procel.missions.xp.granted").increment();
    }

    public void missionXpAmount(int amount) {
        DistributionSummary.builder("procel.missions.xp.amount")
                .description("XP amount granted by automatic mission completion")
                .register(registry)
                .record(Math.max(0, amount));
    }

    public void missionXpDuplicate() {
        counter("procel.missions.xp.duplicates").increment();
    }

    public void missionXpFailure(String type) {
        counter("procel.missions.xp.failures", "type", type == null || type.isBlank() ? "unknown" : type).increment();
    }

    public void droolsCompilation(String mode, Duration duration) {
        counter("procel.missions.drools.compilations", "mode", mode).increment();
        timer("procel.missions.drools.compilation.duration", "mode", mode).record(duration);
    }

    public void droolsCompilationFailure(String mode) {
        counter("procel.missions.drools.compilation.failures", "mode", mode).increment();
    }

    public void droolsCacheHit(String mode) {
        counter("procel.missions.drools.cache.hits", "mode", mode).increment();
    }

    public void droolsCacheMiss(String mode) {
        counter("procel.missions.drools.cache.misses", "mode", mode).increment();
    }

    public void droolsCacheEviction(String mode, long count) {
        counter("procel.missions.drools.cache.evictions", "mode", mode).increment(Math.max(0, count));
    }

    public void droolsEvaluation(String mode, String result, Duration duration) {
        counter("procel.missions.drools.evaluations", "mode", mode, "result", result).increment();
        timer("procel.missions.drools.evaluation.duration", "mode", mode, "result", result).record(duration);
    }

    public void droolsEvaluationFailure(String mode) {
        counter("procel.missions.drools.evaluation.failures", "mode", mode, "result", "failed").increment();
    }

    public void droolsFacts(String mode, long count) {
        DistributionSummary.builder("procel.missions.drools.facts")
                .tags("mode", mode)
                .description("Facts evaluated by Drools mission rule engine")
                .register(registry)
                .record(Math.max(0, count));
    }

    public void droolsLimitRejection(String mode, String limit) {
        counter("procel.missions.drools.limit.rejections", "mode", mode, "limit", limit).increment();
    }

    public void registerMissionBacklogGauge(Object owner, Supplier<Number> supplier) {
        Gauge.builder("procel.missions.backlog", owner, ignored -> supplier.get().doubleValue())
                .register(registry);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }
}
