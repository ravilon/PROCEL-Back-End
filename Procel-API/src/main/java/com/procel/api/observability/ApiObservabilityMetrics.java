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
