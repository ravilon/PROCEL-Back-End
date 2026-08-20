package com.procel.telemetry.observability;

import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryObservabilityMetricsTest {
    private static final Set<String> HIGH_CARDINALITY_TAGS = Set.of(
            "messageId", "sensorId", "rawEventId", "jobId", "windowId", "userId", "payload", "error"
    );

    @Test
    void recordsTelemetryMetricsWithoutHighCardinalityTags() {
        RawTelemetryEventRepository repository = mock(RawTelemetryEventRepository.class);
        when(repository.countByStatus(RawTelemetryStatus.RECEIVED)).thenReturn(2L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TelemetryObservabilityMetrics metrics = new TelemetryObservabilityMetrics(registry, repository);

        metrics.event("REST", "received", Duration.ofMillis(10));
        metrics.event("REST", "duplicate", Duration.ofMillis(5));
        metrics.event("MQTT", "conflict", Duration.ofMillis(7));
        metrics.event("UNKNOWN", "discarded", Duration.ofMillis(1));
        metrics.mqtt("received", Duration.ZERO);
        metrics.mqtt("rejected", Duration.ofMillis(2));
        metrics.mqttReconnect();
        metrics.canonical("REST", "accepted", Duration.ofMillis(20));
        metrics.canonical("REST", "retry", Duration.ofMillis(30));
        metrics.canonical("REST", "failed", Duration.ofMillis(40));

        assertThat(registry.find("procel.telemetry.events").tag("outcome", "received").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("procel.telemetry.mqtt.messages").tag("outcome", "rejected").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("procel.telemetry.mqtt.reconnections").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("procel.telemetry.canonical.retries").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("procel.telemetry.canonical.failures").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("procel.telemetry.backlog").tag("status", "RECEIVED").gauge().value()).isEqualTo(2.0);
        assertNoHighCardinalityTags(registry);
    }

    private static void assertNoHighCardinalityTags(SimpleMeterRegistry registry) {
        for (Meter meter : registry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                assertThat(tag.getKey()).isNotIn(HIGH_CARDINALITY_TAGS);
            }
        }
    }
}
