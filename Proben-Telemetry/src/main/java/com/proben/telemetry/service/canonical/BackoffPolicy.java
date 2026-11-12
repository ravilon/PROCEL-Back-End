package com.proben.telemetry.service.canonical;

import com.proben.telemetry.config.TelemetryProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Computes retry delays from the telemetry worker's configured backoff schedule.
 */
@Component
public class BackoffPolicy {
    private final TelemetryProperties properties;

    /** @param properties application settings containing the retry schedule */
    public BackoffPolicy(TelemetryProperties properties) {
        this.properties = properties;
    }

    /** @param attempts one-based failed-attempt count @return delay before retry */
    public Duration delayForAttempt(int attempts) {
        List<Duration> backoff = properties.getCanonicalWorker().getBackoff();
        if (backoff == null || backoff.isEmpty()) {
            return Duration.ofMinutes(1);
        }
        int index = Math.max(0, Math.min(attempts - 1, backoff.size() - 1));
        return backoff.get(index);
    }
}
