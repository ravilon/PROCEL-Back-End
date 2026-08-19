package com.procel.telemetry.service.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.TelemetrySource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalHttpClientTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void callsPayloadPointerAndRouteSensorInternalRoutesPreservingRawContext() throws Exception {
        AtomicReference<String> payloadPath = new AtomicReference<>();
        AtomicReference<String> routePath = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = server(exchange -> {
            String path = exchange.getRequestURI().getPath();
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            body.set(requestBody);
            if (path.contains("/sensor-1/")) {
                routePath.set(path);
            } else {
                payloadPath.set(path);
            }
            respond(exchange, 201, """
                    {"status":"CREATED","code":"MEASUREMENT_INGESTED","duplicate":false,"medicaoId":"m1","messageId":"raw-msg","apiReceivedAt":"2026-08-19T12:00:02Z"}
                    """);
        });

        var client = new CanonicalIngestClient(objectMapper, properties(), issuer(), java.net.http.HttpClient.newHttpClient());
        client.ingest(event(), profile("profile-payload", CanonicalApiDTOs.SensorResolutionMode.PAYLOAD_POINTER));
        client.ingest(event(), profile("profile-route", CanonicalApiDTOs.SensorResolutionMode.ROUTE_SENSOR));

        assertThat(payloadPath.get()).isEqualTo("/api/sensors/internal/telemetry-events/ingest/integrations/profile-payload");
        assertThat(routePath.get()).isEqualTo("/api/sensors/internal/telemetry-events/sensor-1/ingest/integrations/profile-route");
        var request = objectMapper.readTree(body.get());
        assertThat(request.get("rawTelemetryEventId").asText()).isEqualTo("raw-1");
        assertThat(request.get("originalProducerId").asText()).isEqualTo("original-producer");
        assertThat(request.get("rawMessageId").asText()).isEqualTo("raw-msg");
        assertThat(request.get("payload").get("value").asInt()).isEqualTo(1);
    }

    @Test
    void parsesCreatedDuplicateAndConflictResponses() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server = server(exchange -> {
            int call = count.incrementAndGet();
            if (call == 1) {
                respond(exchange, 201, response("CREATED", "MEASUREMENT_INGESTED", false));
            } else if (call == 2) {
                respond(exchange, 200, response("DUPLICATE", "DUPLICATE_MESSAGE", true));
            } else {
                respond(exchange, 409, response("CONFLICT", "IDEMPOTENCY_CONFLICT", true));
            }
        });
        var client = new CanonicalIngestClient(objectMapper, properties(), issuer(), java.net.http.HttpClient.newHttpClient());
        var profile = profile("profile", CanonicalApiDTOs.SensorResolutionMode.PAYLOAD_POINTER);

        assertThat(client.ingest(event(), profile).code()).isEqualTo("MEASUREMENT_INGESTED");
        assertThat(client.ingest(event(), profile).code()).isEqualTo("DUPLICATE_MESSAGE");
        assertThat(client.ingest(event(), profile).code()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void classifiesTransientAndPermanentHttpErrors() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server = server(exchange -> {
            int call = count.incrementAndGet();
            if (call == 1) {
                respond(exchange, 429, "{\"message\":\"too many\",\"error\":\"TOO_MANY\",\"timestamp\":\"2026-08-19T12:00:00Z\"}");
            } else if (call == 2) {
                respond(exchange, 500, "{\"message\":\"boom\",\"error\":\"BOOM\",\"timestamp\":\"2026-08-19T12:00:00Z\"}");
            } else {
                respond(exchange, 400, "{\"message\":\"bad\",\"error\":\"BAD_REQUEST\",\"timestamp\":\"2026-08-19T12:00:00Z\"}");
            }
        });
        var client = new CanonicalIngestClient(objectMapper, properties(), issuer(), java.net.http.HttpClient.newHttpClient());
        var profile = profile("profile", CanonicalApiDTOs.SensorResolutionMode.PAYLOAD_POINTER);

        assertThatThrownBy(() -> client.ingest(event(), profile)).isInstanceOf(TransientCanonicalException.class);
        assertThatThrownBy(() -> client.ingest(event(), profile)).isInstanceOf(TransientCanonicalException.class);
        assertThatThrownBy(() -> client.ingest(event(), profile))
                .isInstanceOfSatisfying(CanonicalHttpException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(400));
    }

    @Test
    void snapshotIsCachedByConfiguredTtl() throws Exception {
        AtomicInteger count = new AtomicInteger();
        server = server(exchange -> {
            count.incrementAndGet();
            respond(exchange, 200, """
                    {"version":1,"generatedAt":"2026-08-19T12:00:00Z","profiles":[]}
                    """);
        });

        var client = new SensorIntegrationSnapshotClient(objectMapper, properties(), issuer(), java.net.http.HttpClient.newHttpClient());
        client.snapshot();
        client.snapshot();

        assertThat(count.get()).isEqualTo(1);
    }

    private HttpServer server(Handler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/", exchange -> handler.handle(exchange));
        httpServer.start();
        return httpServer;
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String response(String status, String code, boolean duplicate) {
        return """
                {"status":"%s","code":"%s","duplicate":%s,"medicaoId":"m1","messageId":"raw-msg","apiReceivedAt":"2026-08-19T12:00:02Z"}
                """.formatted(status, code, duplicate);
    }

    private TelemetryProperties properties() {
        var properties = new TelemetryProperties();
        properties.getCanonicalWorker().setApiBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.getCanonicalWorker().getJwt().setSecret("jwt-test-secret-with-at-least-32-chars");
        return properties;
    }

    private TelemetryServiceJwtIssuer issuer() {
        return new TelemetryServiceJwtIssuer(objectMapper, properties());
    }

    private RawTelemetryEvent event() {
        var event = new RawTelemetryEvent(
                "original-producer",
                TelemetrySource.REST,
                "raw-msg",
                "sensor-1",
                Instant.parse("2026-08-19T12:00:00Z"),
                Instant.parse("2026-08-19T12:00:01Z"),
                Map.of("value", 1),
                "hash",
                Instant.parse("2026-08-20T12:00:00Z")
        );
        org.springframework.test.util.ReflectionTestUtils.setField(event, "id", "raw-1");
        return event;
    }

    private CanonicalApiDTOs.ProfileSnapshot profile(String id, CanonicalApiDTOs.SensorResolutionMode mode) {
        return new CanonicalApiDTOs.ProfileSnapshot(
                id,
                id,
                TelemetrySource.REST,
                new CanonicalApiDTOs.ParserVersionSnapshot("version-" + id, 1, mode, "/id", null, "/ts", null, "ISO_INSTANT", List.of()),
                List.of(new CanonicalApiDTOs.BindingSnapshot("sensor-1", "Sensor 1"))
        );
    }

    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }
}
