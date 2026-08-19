package com.procel.telemetry.controller;

import com.procel.telemetry.TestJwt;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TelemetryControllerTest {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("procel.security.jwt.secret", () -> TestJwt.SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired RawTelemetryEventRepository repository;
    @Autowired MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void postCreatesDuplicateAndConflictResponses() throws Exception {
        mvc.perform(post("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("ingestor-a", "INGESTOR"))
                        .contentType("application/json")
                        .content(event("msg-1", "sensor-1", "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.duplicate").value(false));

        mvc.perform(post("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("ingestor-a", "INGESTOR"))
                        .contentType("application/json")
                        .content(event("msg-1", "sensor-1", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        mvc.perform(post("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("ingestor-a", "INGESTOR"))
                        .contentType("application/json")
                        .content(event("msg-1", "sensor-1", "2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("RAW_IDEMPOTENCY_CONFLICT"));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void adminCanListFilterPageAndGetById() throws Exception {
        create("msg-a", "sensor-a", "REST", "1");
        create("msg-b", "sensor-b", "MQTT", "2");
        create("msg-c", "sensor-a", "REST", "3");

        String list = mvc.perform(get("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN"))
                        .param("source", "REST")
                        .param("sensorId", "sensor-a")
                        .param("status", "RECEIVED")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andReturn().getResponse().getContentAsString();

        String id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(list).get("content").get(0).get("id").asText();

        mvc.perform(get("/api/telemetry/events/" + id)
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.payload.value").exists());
    }

    @Test
    void adminCanFilterByProducerIdAndMessageId() throws Exception {
        create("msg-a", "sensor-a", "REST", "1");
        create("msg-b", "sensor-b", "MQTT", "2");

        mvc.perform(get("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN"))
                        .param("producerId", "admin")
                        .param("messageId", "msg-b")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].messageId").value("msg-b"));
    }

    @Test
    void adminCanReprocessCanonicalFailedAndAuditPreviousProcessing() throws Exception {
        insertRaw("reprocess-ok", "msg-reprocess", Instant.parse("2026-08-19T12:00:00Z"),
                RawTelemetryStatus.CANONICAL_FAILED);

        mvc.perform(post("/api/telemetry/events/reprocess-ok/reprocess")
                        .header("Authorization", TestJwt.bearer("admin-user", "ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"  corrigir parser ativo  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("reprocess-ok"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.previousStatus").value("CANONICAL_FAILED"))
                .andExpect(jsonPath("$.reprocessCount").value(1))
                .andExpect(jsonPath("$.requestedBy").value("admin-user"));

        Document event = mongoTemplate.getCollection("raw_telemetry_events")
                .find(new Document("_id", "reprocess-ok"))
                .first();
        assertThat(event).isNotNull();
        assertThat(event.getString("producerId")).isEqualTo("admin");
        assertThat(event.getString("messageId")).isEqualTo("msg-reprocess");
        assertThat(event.getString("payloadHash")).isEqualTo("msg-reprocess");
        assertThat(((Document) event.get("payload")).getString("value")).isEqualTo("msg-reprocess");

        Document processing = event.get("processing", Document.class);
        assertThat(processing.getInteger("attempts")).isZero();
        assertThat(processing).doesNotContainKeys(
                "lastError",
                "lastAttemptAt",
                "nextAttemptAt",
                "lockedAt",
                "workerId",
                "canonicalMeasurementId",
                "profileId",
                "parserVersionId"
        );

        Document reprocessing = event.get("reprocessing", Document.class);
        assertThat(reprocessing.getInteger("count")).isEqualTo(1);
        assertThat(reprocessing.getString("lastRequestedBy")).isEqualTo("admin-user");
        assertThat(reprocessing.getString("lastReason")).isEqualTo("corrigir parser ativo");

        List<Document> audit = event.getList("reprocessAudit", Document.class);
        assertThat(audit).hasSize(1);
        Document entry = audit.getFirst();
        assertThat(entry.getString("previousStatus")).isEqualTo("CANONICAL_FAILED");
        assertThat(entry.getString("lastError")).isEqualTo("PROFILE_NOT_FOUND");
        assertThat(entry.getInteger("attempts")).isEqualTo(3);
        assertThat(entry.getString("canonicalMeasurementId")).isEqualTo("measurement-1");
        assertThat(entry.getString("profileId")).isEqualTo("profile-1");
        assertThat(entry.getString("parserVersionId")).isEqualTo("parser-1");
        assertThat(entry.getString("requestedBy")).isEqualTo("admin-user");
        assertThat(entry.getString("reason")).isEqualTo("corrigir parser ativo");
    }

    @Test
    void reprocessRequiresAdminReasonAndReprocessableStatus() throws Exception {
        insertRaw("reprocess-conflict", "msg-conflict", Instant.parse("2026-08-19T12:00:00Z"),
                RawTelemetryStatus.CANONICAL_CONFLICT);
        insertRaw("reprocess-discarded", "msg-discarded", Instant.parse("2026-08-19T12:00:01Z"),
                RawTelemetryStatus.DISCARDED);
        insertRaw("reprocess-blocked", "msg-blocked", Instant.parse("2026-08-19T12:00:02Z"),
                RawTelemetryStatus.CANONICAL_ACCEPTED);

        mvc.perform(post("/api/telemetry/events/reprocess-conflict/reprocess")
                        .header("Authorization", TestJwt.bearer("ingestor", "INGESTOR"))
                        .contentType("application/json")
                        .content("{\"reason\":\"retry\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mvc.perform(post("/api/telemetry/events/reprocess-conflict/reprocess")
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(post("/api/telemetry/events/reprocess-blocked/reprocess")
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"retry\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TELEMETRY_REPROCESS_NOT_ALLOWED"));

        mvc.perform(post("/api/telemetry/events/reprocess-discarded/reprocess")
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"retry discarded\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("DISCARDED"));
    }

    @Test
    void concurrentReprocessOnlyTransitionsOnce() throws Exception {
        insertRaw("reprocess-race", "msg-race", Instant.parse("2026-08-19T12:00:00Z"),
                RawTelemetryStatus.CANONICAL_FAILED);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        int statusCode = mvc.perform(post("/api/telemetry/events/reprocess-race/reprocess")
                                        .header("Authorization", TestJwt.bearer("admin", "ADMIN"))
                                        .contentType("application/json")
                                        .content("{\"reason\":\"race\"}"))
                                .andReturn().getResponse().getStatus();
                        if (statusCode == 200) accepted.incrementAndGet();
                        if (statusCode == 409) conflicts.incrementAndGet();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(accepted.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        Document event = mongoTemplate.getCollection("raw_telemetry_events")
                .find(new Document("_id", "reprocess-race"))
                .first();
        List<Document> raceAudit = event.getList("reprocessAudit", Document.class);
        assertThat(raceAudit).hasSize(1);
    }

    @Test
    void listUsesDeterministicOrderingWhenReceivedAtTies() throws Exception {
        Instant receivedAt = Instant.parse("2026-08-19T12:00:00Z");
        insertRaw("event-a", "msg-a", receivedAt);
        insertRaw("event-b", "msg-b", receivedAt);

        mvc.perform(get("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN"))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("event-b"))
                .andExpect(jsonPath("$.content[1].id").value("event-a"));
    }

    @Test
    void securityRequiresAuthenticationAndRoles() throws Exception {
        mvc.perform(post("/api/telemetry/events")
                        .contentType("application/json")
                        .content(event("msg-auth", "sensor-1", "1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        mvc.perform(post("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("user", "USUARIO"))
                        .contentType("application/json")
                        .content(event("msg-auth", "sensor-1", "1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mvc.perform(get("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("ingestor", "INGESTOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsPayloadAboveLimitAndInvalidJson() throws Exception {
        mvc.perform(post("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("ingestor", "INGESTOR"))
                        .contentType("application/json")
                        .content("x".repeat(262145)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"));

        mvc.perform(post("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("ingestor", "INGESTOR"))
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_JSON"));
    }

    @Test
    void rejectsInvalidContracts() throws Exception {
        mvc.perform(post("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("ingestor", "INGESTOR"))
                        .contentType("application/json")
                        .content("""
                                {"source":"UNKNOWN","messageId":"msg","payload":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SOURCE_INVALID"));

        mvc.perform(get("/api/telemetry/events/missing")
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private void create(String messageId, String sensorId, String source, String value) throws Exception {
        mvc.perform(post("/api/telemetry/events")
                        .header("Authorization", TestJwt.bearer("admin", "ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "source":"%s",
                                  "messageId":"%s",
                                  "sensorId":"%s",
                                  "sourceTimestamp":"2026-08-19T12:00:00Z",
                                  "payload":{"value":%s}
                                }
                                """.formatted(source, messageId, sensorId, value)))
                .andExpect(status().isCreated());
    }

    private String event(String messageId, String sensorId, String value) {
        return """
                {
                  "source":"REST",
                  "messageId":"%s",
                  "sensorId":"%s",
                  "sourceTimestamp":"2026-08-19T12:00:00Z",
                  "payload":{"value":%s}
                }
                """.formatted(messageId, sensorId, value);
    }

    private void insertRaw(String id, String messageId, Instant receivedAt) {
        insertRaw(id, messageId, receivedAt, RawTelemetryStatus.RECEIVED);
    }

    private void insertRaw(String id, String messageId, Instant receivedAt, RawTelemetryStatus status) {
        mongoTemplate.getCollection("raw_telemetry_events").insertOne(new Document()
                .append("_id", id)
                .append("producerId", "admin")
                .append("source", "REST")
                .append("messageId", messageId)
                .append("sensorId", "sensor-order")
                .append("receivedAt", Date.from(receivedAt))
                .append("payload", new Document("value", messageId))
                .append("payloadHash", messageId)
                .append("status", status.name())
                .append("processing", new Document()
                        .append("attempts", 3)
                        .append("lastAttemptAt", Date.from(receivedAt.plusSeconds(10)))
                        .append("nextAttemptAt", Date.from(receivedAt.plusSeconds(20)))
                        .append("lockedAt", Date.from(receivedAt.plusSeconds(30)))
                        .append("workerId", "worker-1")
                        .append("lastError", "PROFILE_NOT_FOUND")
                        .append("canonicalMeasurementId", "measurement-1")
                        .append("profileId", "profile-1")
                        .append("parserVersionId", "parser-1"))
                .append("expiresAt", Date.from(receivedAt.plusSeconds(86_400))));
    }
}
