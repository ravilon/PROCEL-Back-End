package com.procel.telemetry.service.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.entity.RawTelemetryEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class CanonicalIngestClient {
    private final ObjectMapper objectMapper;
    private final TelemetryProperties properties;
    private final TelemetryServiceJwtIssuer jwtIssuer;
    private final HttpClient httpClient;

    @Autowired
    public CanonicalIngestClient(
            ObjectMapper objectMapper,
            TelemetryProperties properties,
            TelemetryServiceJwtIssuer jwtIssuer
    ) {
        this(objectMapper, properties, jwtIssuer, HttpClient.newHttpClient());
    }

    CanonicalIngestClient(
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

    public CanonicalApiDTOs.CanonicalIngestResponse ingest(
            RawTelemetryEvent event,
            CanonicalApiDTOs.ProfileSnapshot profile
    ) {
        CanonicalApiDTOs.TelemetryRawIntegrationIngestRequest body =
                new CanonicalApiDTOs.TelemetryRawIntegrationIngestRequest(
                        event.getId(),
                        event.getProducerId(),
                        event.getMessageId(),
                        event.getReceivedAt(),
                        event.getSourceTimestamp(),
                        event.getPayload()
                );
        String path = path(event, profile);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(properties.getCanonicalWorker().getPollInterval())
                .header("Authorization", jwtIssuer.bearerToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json(body)))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            throw new TransientCanonicalException("Canonical ingest failed with HTTP " + response.statusCode());
        }
        if (response.statusCode() == 409) {
            return readCanonicalResponse(response);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            CanonicalApiDTOs.ErrorResponse error = error(response);
            throw new CanonicalHttpException(response.statusCode(), error.error(), error.message());
        }
        return readCanonicalResponse(response);
    }

    private CanonicalApiDTOs.CanonicalIngestResponse readCanonicalResponse(HttpResponse<String> response) {
        try {
            return objectMapper.readValue(response.body(), CanonicalApiDTOs.CanonicalIngestResponse.class);
        } catch (IOException ex) {
            throw new CanonicalHttpException(502, "CANONICAL_RESPONSE_INVALID", "Invalid canonical ingest response.");
        }
    }

    private String path(RawTelemetryEvent event, CanonicalApiDTOs.ProfileSnapshot profile) {
        if (profile.activeParserVersion().sensorResolutionMode() == CanonicalApiDTOs.SensorResolutionMode.ROUTE_SENSOR) {
            return "/api/sensors/internal/telemetry-events/" + pathSegment(event.getSensorId())
                    + "/ingest/integrations/" + profile.id();
        }
        return "/api/sensors/internal/telemetry-events/ingest/integrations/" + profile.id();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new TransientCanonicalException("Canonical ingest timed out", ex);
        } catch (IOException ex) {
            throw new TransientCanonicalException("Canonical ingest failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TransientCanonicalException("Canonical ingest interrupted", ex);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to serialize canonical request", ex);
        }
    }

    private CanonicalApiDTOs.ErrorResponse error(HttpResponse<String> response) {
        try {
            return objectMapper.readValue(response.body(), CanonicalApiDTOs.ErrorResponse.class);
        } catch (Exception ex) {
            return new CanonicalApiDTOs.ErrorResponse(
                    "Canonical ingest failed with HTTP " + response.statusCode(),
                    "HTTP_" + response.statusCode(),
                    Instant.now()
            );
        }
    }

    private URI uri(String path) {
        return URI.create(trimTrailingSlash(properties.getCanonicalWorker().getApiBaseUrl()) + path);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
