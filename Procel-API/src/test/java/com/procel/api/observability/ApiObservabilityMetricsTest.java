package com.procel.api.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiObservabilityMetricsTest {
    private static final Set<String> HIGH_CARDINALITY_TAGS = Set.of(
            "messageId", "sensorId", "rawEventId", "jobId", "windowId", "userId", "payload", "error"
    );

    @Test
    void recordsBusinessMetricsWithoutHighCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApiObservabilityMetrics metrics = new ApiObservabilityMetrics(registry);

        metrics.aggregationJobCreated("created");
        metrics.windowProcessed("completed", 1, Duration.ofMillis(25));
        metrics.windowProcessed("retry", 2, Duration.ofMillis(30));
        metrics.bucketsPersisted(3);
        metrics.analyticsQuery("list", "success", Duration.ofMillis(10));
        metrics.analyticsQuery("summary", "error", Duration.ofMillis(15));

        assertThat(registry.find("procel.analytics.aggregation.jobs").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("procel.analytics.aggregation.windows.completed").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("procel.analytics.aggregation.windows.retries").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("procel.analytics.buckets.persisted").summary().count()).isEqualTo(1L);
        assertThat(registry.find("procel.analytics.query.errors").counter().count()).isEqualTo(1.0);
        assertNoHighCardinalityTags(registry);
    }

    private static void assertNoHighCardinalityTags(SimpleMeterRegistry registry) {
        for (Meter meter : registry.getMeters()) {
            assertThat(meter.getId().getTags().stream()
                            .map((Tag tag) -> tag.getKey()))
                    .doesNotContainAnyElementsOf(HIGH_CARDINALITY_TAGS);
        }
    }
}
