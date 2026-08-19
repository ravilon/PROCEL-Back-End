package com.procel.telemetry.service.canonical;

import com.procel.telemetry.config.TelemetryProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class BackoffPolicy {
    private final TelemetryProperties properties;

    public BackoffPolicy(TelemetryProperties properties) {
        this.properties = properties;
    }

    public Duration delayForAttempt(int attempts) {
        List<Duration> backoff = properties.getCanonicalWorker().getBackoff();
        if (backoff == null || backoff.isEmpty()) {
            return Duration.ofMinutes(1);
        }
        int index = Math.max(0, Math.min(attempts - 1, backoff.size() - 1));
        return backoff.get(index);
    }
}
