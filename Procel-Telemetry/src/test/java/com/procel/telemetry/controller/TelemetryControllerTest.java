package com.procel.telemetry.controller;

import com.procel.telemetry.TestJwt;
import com.procel.telemetry.entity.RawTelemetryEvent;
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
                .andExpect(status().isPayloadTooLarge())
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
        mongoTemplate.getCollection("raw_telemetry_events").insertOne(new Document()
                .append("_id", id)
                .append("producerId", "admin")
                .append("source", "REST")
                .append("messageId", messageId)
                .append("sensorId", "sensor-order")
                .append("receivedAt", Date.from(receivedAt))
                .append("payload", new Document("value", messageId))
                .append("payloadHash", messageId)
                .append("status", "RECEIVED")
                .append("processing", new Document())
                .append("expiresAt", Date.from(receivedAt.plusSeconds(86_400))));
    }
}
