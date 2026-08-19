package com.procel.telemetry.service.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.config.TelemetryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

@Service
public class SensorIntegrationSnapshotClient {
    private final ObjectMapper objectMapper;
    private final TelemetryProperties properties;
    private final TelemetryServiceJwtIssuer jwtIssuer;
    private final HttpClient httpClient;
    private CacheEntry cache;

    @Autowired
    public SensorIntegrationSnapshotClient(
            ObjectMapper objectMapper,
            TelemetryProperties properties,
            TelemetryServiceJwtIssuer jwtIssuer
    ) {
        this(objectMapper, properties, jwtIssuer, HttpClient.newHttpClient());
    }

    SensorIntegrationSnapshotClient(
            ObjectMapper objectMapper,
            TelemetryProperties properties,
            TelemetryServiceJwtIssuer jwtIssuer,
            HttpClient httpClient
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.jwtIssuer = jwtIssuer;
        this.httpClient = httpClient;
    }

    public synchronized CanonicalApiDTOs.SnapshotResponse snapshot() {
        Instant now = Instant.now();
        if (cache != null && now.isBefore(cache.expiresAt())) {
            return cache.snapshot();
        }
        CanonicalApiDTOs.SnapshotResponse snapshot = fetchSnapshot();
        cache = new CacheEntry(snapshot, now.plus(properties.getCanonicalWorker().getSnapshotCacheTtl()));
        return snapshot;
    }

    void clearCache() {
        cache = null;
    }

    private CanonicalApiDTOs.SnapshotResponse fetchSnapshot() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/sensor-integrations/snapshot"))
                .timeout(properties.getCanonicalWorker().getPollInterval())
                .header("Authorization", jwtIssuer.bearerToken())
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            throw new TransientCanonicalException("Snapshot request failed with HTTP " + response.statusCode());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            CanonicalApiDTOs.ErrorResponse error = error(response.body());
            throw new CanonicalHttpException(response.statusCode(), error.error(), error.message());
        }
        try {
            return objectMapper.readValue(response.body(), CanonicalApiDTOs.SnapshotResponse.class);
        } catch (IOException ex) {
            throw new CanonicalHttpException(502, "SNAPSHOT_INVALID", "Invalid snapshot response.");
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new TransientCanonicalException("Snapshot request timed out", ex);
        } catch (IOException ex) {
            throw new TransientCanonicalException("Snapshot request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TransientCanonicalException("Snapshot request interrupted", ex);
        }
    }

    private URI uri(String path) {
        return URI.create(trimTrailingSlash(properties.getCanonicalWorker().getApiBaseUrl()) + path);
    }

    private CanonicalApiDTOs.ErrorResponse error(String body) {
        try {
            return objectMapper.readValue(body, CanonicalApiDTOs.ErrorResponse.class);
        } catch (Exception ex) {
            return new CanonicalApiDTOs.ErrorResponse("HTTP request failed", "HTTP_" + ex.getClass().getSimpleName(), Instant.now());
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record CacheEntry(CanonicalApiDTOs.SnapshotResponse snapshot, Instant expiresAt) {}
}
