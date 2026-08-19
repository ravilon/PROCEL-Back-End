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
    private Mqtt mqtt = new Mqtt();

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

    public Mqtt getMqtt() {
        return mqtt;
    }

    public void setMqtt(Mqtt mqtt) {
        this.mqtt = mqtt;
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

    public static class Mqtt {
        private boolean enabled = false;
        private String brokerUrl = "tcp://localhost:1883";
        private String clientId = "procel-telemetry";
        private List<String> topicFilters = new ArrayList<>(List.of(
                "procel/telemetry/v1/+/+/events",
                "procel/telemetry/v1/+/events"
        ));
        private int qos = 1;
        private boolean rejectRetained = true;
        private boolean cleanStart = false;
        private Duration sessionExpiry = Duration.ofDays(1);
        private Duration connectionTimeout = Duration.ofSeconds(10);
        private Duration keepAlive = Duration.ofSeconds(30);
        private boolean automaticReconnect = true;
        private Duration reconnectMinDelay = Duration.ofSeconds(1);
        private Duration reconnectMaxDelay = Duration.ofSeconds(30);
        private String username;
        private String password;
        private Tls tls = new Tls();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBrokerUrl() { return brokerUrl; }
        public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public List<String> getTopicFilters() { return topicFilters; }
        public void setTopicFilters(List<String> topicFilters) { this.topicFilters = topicFilters; }
        public int getQos() { return qos; }
        public void setQos(int qos) { this.qos = qos; }
        public boolean isRejectRetained() { return rejectRetained; }
        public void setRejectRetained(boolean rejectRetained) { this.rejectRetained = rejectRetained; }
        public boolean isCleanStart() { return cleanStart; }
        public void setCleanStart(boolean cleanStart) { this.cleanStart = cleanStart; }
        public Duration getSessionExpiry() { return sessionExpiry; }
        public void setSessionExpiry(Duration sessionExpiry) { this.sessionExpiry = sessionExpiry; }
        public Duration getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        public Duration getKeepAlive() { return keepAlive; }
        public void setKeepAlive(Duration keepAlive) { this.keepAlive = keepAlive; }
        public boolean isAutomaticReconnect() { return automaticReconnect; }
        public void setAutomaticReconnect(boolean automaticReconnect) { this.automaticReconnect = automaticReconnect; }
        public Duration getReconnectMinDelay() { return reconnectMinDelay; }
        public void setReconnectMinDelay(Duration reconnectMinDelay) { this.reconnectMinDelay = reconnectMinDelay; }
        public Duration getReconnectMaxDelay() { return reconnectMaxDelay; }
        public void setReconnectMaxDelay(Duration reconnectMaxDelay) { this.reconnectMaxDelay = reconnectMaxDelay; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public Tls getTls() { return tls; }
        public void setTls(Tls tls) { this.tls = tls; }
    }

    public static class Tls {
        private boolean enabled = false;
        private String trustStore;
        private String trustStorePassword;
        private String keyStore;
        private String keyStorePassword;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTrustStore() { return trustStore; }
        public void setTrustStore(String trustStore) { this.trustStore = trustStore; }
        public String getTrustStorePassword() { return trustStorePassword; }
        public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }
        public String getKeyStore() { return keyStore; }
        public void setKeyStore(String keyStore) { this.keyStore = keyStore; }
        public String getKeyStorePassword() { return keyStorePassword; }
        public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }
    }
}
