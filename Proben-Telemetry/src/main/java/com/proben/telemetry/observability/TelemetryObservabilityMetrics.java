package com.proben.telemetry.observability;

import com.proben.telemetry.entity.RawTelemetryStatus;
import com.proben.telemetry.repository.RawTelemetryEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Componente de telemetry observability metrics usado pelo modulo Telemetry.
 */
@Component
public class TelemetryObservabilityMetrics {
    private static final String UNKNOWN = "UNKNOWN";

    private final MeterRegistry registry;
    private final RawTelemetryEventRepository repository;

    public TelemetryObservabilityMetrics(MeterRegistry registry, RawTelemetryEventRepository repository) {
        this.registry = registry;
        this.repository = repository;
        for (RawTelemetryStatus status : RawTelemetryStatus.values()) {
            registry.gauge("proben.telemetry.backlog", Tags.of("status", status.name()), status, this::backlog);
        }
    }

    public void event(String source, String outcome, Duration duration) {
        counter("proben.telemetry.events", "source", source(source), "outcome", outcome).increment();
        timer("proben.telemetry.events.duration", "source", source(source), "outcome", outcome).record(duration);
    }

    public void mqtt(String outcome, Duration duration) {
        counter("proben.telemetry.mqtt.messages", "outcome", outcome).increment();
        timer("proben.telemetry.mqtt.duration", "outcome", outcome).record(duration);
    }

    public void mqttReconnect() {
        counter("proben.telemetry.mqtt.reconnections", "outcome", "success").increment();
    }

    public void canonical(String source, String outcome, Duration duration) {
        counter("proben.telemetry.canonical.results", "source", source(source), "outcome", outcome).increment();
        timer("proben.telemetry.canonical.duration", "source", source(source), "outcome", outcome).record(duration);
        if ("retry".equals(outcome)) {
            counter("proben.telemetry.canonical.retries", "source", source(source), "outcome", outcome).increment();
        }
        if ("failed".equals(outcome)) {
            counter("proben.telemetry.canonical.failures", "source", source(source), "outcome", outcome).increment();
        }
    }

    private double backlog(RawTelemetryStatus status) {
        try {
            return repository.countByStatus(status);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }

    private static String source(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
