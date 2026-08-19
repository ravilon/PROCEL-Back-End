package com.procel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "procel.analytics.buckets")
public class AnalyticsBucketQueryProperties {
    private Duration maxPeriod = Duration.ofDays(366);
    private int maxPageSize = 200;

    public Duration getMaxPeriod() {
        return maxPeriod;
    }

    public void setMaxPeriod(Duration maxPeriod) {
        this.maxPeriod = maxPeriod;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
}
