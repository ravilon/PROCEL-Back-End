package com.procel.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "procel.integrations.parser")
public class SensorIntegrationParserProperties {
    private int maxPayloadBytes = 262144;
    private int maxMappings = 100;
    private int maxDepth = 64;

    public int getMaxPayloadBytes() { return maxPayloadBytes; }
    public void setMaxPayloadBytes(int maxPayloadBytes) { this.maxPayloadBytes = maxPayloadBytes; }
    public int getMaxMappings() { return maxMappings; }
    public void setMaxMappings(int maxMappings) { this.maxMappings = maxMappings; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
}
