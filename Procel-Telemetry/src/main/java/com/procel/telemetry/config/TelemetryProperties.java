package com.procel.telemetry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "procel.telemetry")
public class TelemetryProperties {
    private int maxPayloadBytes = 262144;
    private int retentionDays = 30;
    private CanonicalWorker canonicalWorker = new CanonicalWorker();

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public CanonicalWorker getCanonicalWorker() {
        return canonicalWorker;
    }

    public void setCanonicalWorker(CanonicalWorker canonicalWorker) {
        this.canonicalWorker = canonicalWorker;
    }

    public static class CanonicalWorker {
        private boolean enabled = false;
        private String apiBaseUrl = "http://localhost:8080";
        private Duration pollInterval = Duration.ofSeconds(5);
        private Duration leaseTimeout = Duration.ofMinutes(5);
        private int batchSize = 10;
        private int maxAttempts = 5;
        private List<Duration> backoff = new ArrayList<>(List.of(
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                Duration.ofHours(2)
        ));
        private Duration snapshotCacheTtl = Duration.ofMinutes(5);
        private Jwt jwt = new Jwt();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
        public Duration getPollInterval() { return pollInterval; }
        public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
        public Duration getLeaseTimeout() { return leaseTimeout; }
        public void setLeaseTimeout(Duration leaseTimeout) { this.leaseTimeout = leaseTimeout; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public List<Duration> getBackoff() { return backoff; }
        public void setBackoff(List<Duration> backoff) { this.backoff = backoff; }
        public Duration getSnapshotCacheTtl() { return snapshotCacheTtl; }
        public void setSnapshotCacheTtl(Duration snapshotCacheTtl) { this.snapshotCacheTtl = snapshotCacheTtl; }
        public Jwt getJwt() { return jwt; }
        public void setJwt(Jwt jwt) { this.jwt = jwt; }
    }

    public static class Jwt {
        private String subject = "procel-telemetry";
        private String secret = "change-this-local-development-secret-32chars";
        private Duration ttl = Duration.ofMinutes(5);

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
    }
}
