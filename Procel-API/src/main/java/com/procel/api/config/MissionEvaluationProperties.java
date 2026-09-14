package com.procel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "procel.missions.evaluation")
public class MissionEvaluationProperties {
    private boolean workerEnabled = false;
    private Duration fixedDelay = Duration.ofSeconds(5);
    private int batchSize = 20;
    private Duration leaseDuration = Duration.ofMinutes(1);
    private int maxAttempts = 5;
    private Duration initialBackoff = Duration.ofSeconds(5);
    private Duration maxBackoff = Duration.ofMinutes(5);
    private ZoneId academicZone = ZoneId.of("America/Sao_Paulo");
    private TemporalWindows temporalWindows = new TemporalWindows();

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
        this.fixedDelay = fixedDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    public ZoneId getAcademicZone() {
        return academicZone;
    }

    public void setAcademicZone(ZoneId academicZone) {
        this.academicZone = academicZone == null ? ZoneId.of("America/Sao_Paulo") : academicZone;
    }

    public TemporalWindows getTemporalWindows() {
        return temporalWindows;
    }

    public void setTemporalWindows(TemporalWindows temporalWindows) {
        this.temporalWindows = temporalWindows == null ? new TemporalWindows() : temporalWindows;
    }

    public static class TemporalWindows {
        private boolean workerEnabled = false;
        private Duration fixedDelay = Duration.ofSeconds(5);
        private int batchSize = 20;
        private Duration leaseDuration = Duration.ofMinutes(1);
        private int maxAttempts = 5;
        private int maxBacklog = 10_000;
        private Duration initialBackoff = Duration.ofSeconds(5);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private Duration maximumSampleGap = Duration.ofMinutes(5);
        private Duration maximumWindowDuration = Duration.ofHours(24);

        public boolean isWorkerEnabled() { return workerEnabled; }
        public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
        public Duration getFixedDelay() { return fixedDelay; }
        public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = positiveOrDefault(fixedDelay, Duration.ofSeconds(5)); }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = Math.max(1, batchSize); }
        public Duration getLeaseDuration() { return leaseDuration; }
        public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = positiveOrDefault(leaseDuration, Duration.ofMinutes(1)); }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(1, maxAttempts); }
        public int getMaxBacklog() { return maxBacklog; }
        public void setMaxBacklog(int maxBacklog) { this.maxBacklog = Math.max(1, maxBacklog); }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = positiveOrDefault(initialBackoff, Duration.ofSeconds(5)); }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = positiveOrDefault(maxBackoff, Duration.ofMinutes(5)); }
        public Duration getMaximumSampleGap() { return maximumSampleGap; }
        public void setMaximumSampleGap(Duration maximumSampleGap) { this.maximumSampleGap = positiveOrDefault(maximumSampleGap, Duration.ofMinutes(5)); }
        public Duration getMaximumWindowDuration() { return maximumWindowDuration; }
        public void setMaximumWindowDuration(Duration maximumWindowDuration) { this.maximumWindowDuration = positiveOrDefault(maximumWindowDuration, Duration.ofHours(24)); }

        private static Duration positiveOrDefault(Duration value, Duration fallback) {
            return value == null || value.isZero() || value.isNegative() ? fallback : value;
        }
    }
}
