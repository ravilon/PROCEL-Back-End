package com.procel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "procel.analytics.aggregation")
public class AnalyticsAggregationProperties {
    private boolean workerEnabled = false;
    private Duration pollInterval = Duration.ofSeconds(5);
    private Duration leaseTimeout = Duration.ofMinutes(10);
    private int maxAttempts = 3;
    private Duration backoff = Duration.ofMinutes(1);
    private Duration maxInterval = Duration.ofDays(366);
    private Duration minWindow = Duration.ofMinutes(1);
    private Duration maxWindow = Duration.ofDays(31);
    private int maxWindows = 10_000;
    private int measurementPageSize = 500;
    private int batchSize = 5;

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getLeaseTimeout() {
        return leaseTimeout;
    }

    public void setLeaseTimeout(Duration leaseTimeout) {
        this.leaseTimeout = leaseTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBackoff() {
        return backoff;
    }

    public void setBackoff(Duration backoff) {
        this.backoff = backoff;
    }

    public Duration getMaxInterval() {
        return maxInterval;
    }

    public void setMaxInterval(Duration maxInterval) {
        this.maxInterval = maxInterval;
    }

    public Duration getMinWindow() {
        return minWindow;
    }

    public void setMinWindow(Duration minWindow) {
        this.minWindow = minWindow;
    }

    public Duration getMaxWindow() {
        return maxWindow;
    }

    public void setMaxWindow(Duration maxWindow) {
        this.maxWindow = maxWindow;
    }

    public int getMaxWindows() {
        return maxWindows;
    }

    public void setMaxWindows(int maxWindows) {
        this.maxWindows = maxWindows;
    }

    public int getMeasurementPageSize() {
        return measurementPageSize;
    }

    public void setMeasurementPageSize(int measurementPageSize) {
        this.measurementPageSize = measurementPageSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
