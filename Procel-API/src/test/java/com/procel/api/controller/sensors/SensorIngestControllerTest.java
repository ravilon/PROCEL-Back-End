package com.procel.api.controller.sensors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.entity.rooms.*;
import com.procel.api.entity.sensors.*;
import com.procel.api.repository.rooms.*;
import com.procel.api.repository.sensors.*;
import com.procel.api.service.sensors.ParametroQualificacaoService;
import com.procel.api.service.sensors.SensorIngestOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SensorIngestControllerTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("procel.security.bootstrap-admin.enabled", () -> "false");
        registry.add("procel.sensors.seed-path", () -> "missing-test-sensors.json");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SensorIngestOrchestrator orchestrator;
    @Autowired CampusRepository campusRepo;
    @Autowired UnidadeRepository unidadeRepo;
    @Autowired PredioRepository predioRepo;
    @Autowired CompartimentoRepository compartimentoRepo;
    @Autowired TipoDeSensorRepository tipoRepo;
    @Autowired SensorRepository sensorRepo;
    @Autowired ParametroDefRepository parametroRepo;
    @Autowired MedicaoRepository medicaoRepo;
    @Autowired MedicaoIngestaoMetadataRepository metadataRepo;
    @MockitoBean ParametroQualificacaoService qualificacaoService;

    private String sensorId;

    @BeforeEach
    void setUp() {
        sensorId = "SII-TEST-" + UUID.randomUUID();
        Campus campus = campusRepo.save(new Campus("Campus " + sensorId));
        Unidade unidade = unidadeRepo.save(new Unidade("Unidade " + sensorId));
        Predio predio = predioRepo.save(new Predio(campus, "Predio " + sensorId));
        Compartimento compartimento = compartimentoRepo.save(
                new Compartimento("ROOM-" + sensorId, predio, unidade, "Sala " + sensorId, "Sala"));
        TipoDeSensor tipo = tipoRepo.save(new TipoDeSensor("TYPE-" + sensorId));
        parametroRepo.save(new ParametroDef(tipo, "temperature_c", "Temperatura", DataType.NUMERIC, "C"));
        parametroRepo.save(new ParametroDef(tipo, "presence", "Presenca", DataType.BOOLEAN, null));
        parametroRepo.save(new ParametroDef(tipo, "label", "Texto", DataType.TEXT, null));
        sensorRepo.save(new Sensor(sensorId, "Sensor " + sensorId, tipo, compartimento));
    }

    @Test
    void canonicalIngestCreatesMeasurementAndDuplicateReturnsExistingMeasurement() throws Exception {
        String body = objectMapper.writeValueAsString(request("msg-1", Map.of(
                "temperature_c", new BigDecimal("23.70"),
                "presence", true,
                "label", "ok"
        )));

        String created = mvc.perform(post("/api/sensors/ingest")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MEASUREMENT_INGESTED"))
                .andReturn().getResponse().getContentAsString();

        String duplicate = mvc.perform(post("/api/sensors/ingest")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MESSAGE"))
                .andReturn().getResponse().getContentAsString();

        UUID createdId = UUID.fromString(objectMapper.readTree(created).get("medicaoId").asText());
        UUID duplicateId = UUID.fromString(objectMapper.readTree(duplicate).get("medicaoId").asText());
        assertThat(duplicateId).isEqualTo(createdId);
        assertThat(medicaoRepo.count()).isGreaterThanOrEqualTo(1);
        assertThat(metadataRepo.findByProducerIdAndSensor_ExternalIdAndMessageId(
                "ingestor-test", sensorId, "msg-1")).isPresent();
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadReturnsConflict() throws Exception {
        mvc.perform(post("/api/sensors/ingest")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request("msg-conflict", Map.of(
                                "temperature_c", new BigDecimal("23.7")
                        )))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/sensors/ingest")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request("msg-conflict", Map.of(
                                "temperature_c", new BigDecimal("24.1")
                        )))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void validatesRequestAndDomainErrors() throws Exception {
        mvc.perform(post("/api/sensors/ingest")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request("", Map.of("temperature_c", 1)))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/sensors/ingest")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request("msg-empty", Map.of()))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/sensors/ingest")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new SensorIngestDTOs.CanonicalIngestRequest(
                                "msg-missing-sensor",
                                "missing-sensor",
                                Instant.parse("2026-08-11T23:30:00Z"),
                                MedicaoIngestaoSource.MQTT,
                                null,
                                Map.of("temperature_c", 1)
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SENSOR_NOT_FOUND"));

        mvc.perform(post("/api/sensors/ingest")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request("msg-missing-param", Map.of("missing", 1)))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("PARAMETER_NOT_ACCEPTED"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/sensors/ingest")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request("msg-auth", Map.of("temperature_c", 1)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnauthorizedRole() throws Exception {
        mvc.perform(post("/api/sensors/ingest")
                        .with(user("ordinary-user").roles("USUARIO"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request("msg-role", Map.of("temperature_c", 1)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentEquivalentRequestsPersistOnlyOnce() throws Exception {
        var request = request("msg-concurrent-" + UUID.randomUUID(), Map.of(
                "temperature_c", new BigDecimal("23.7"),
                "presence", true
        ));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<SensorIngestOrchestrator.IngestOutcome>> tasks = List.of(
                    () -> orchestrator.ingest("producer-concurrent", request),
                    () -> orchestrator.ingest("producer-concurrent", request)
            );
            List<Future<SensorIngestOrchestrator.IngestOutcome>> futures = executor.invokeAll(tasks);
            List<SensorIngestOrchestrator.IngestOutcome> outcomes = new ArrayList<>();
            for (Future<SensorIngestOrchestrator.IngestOutcome> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(outcomes).extracting(outcome -> outcome.response().code())
                    .containsExactlyInAnyOrder("MEASUREMENT_INGESTED", "DUPLICATE_MESSAGE");
            assertThat(metadataRepo.findByProducerIdAndSensor_ExternalIdAndMessageId(
                    "producer-concurrent", sensorId, request.messageId())).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDivergentRequestsReturnCreatedAndConflict() throws Exception {
        String messageId = "msg-concurrent-conflict-" + UUID.randomUUID();
        var first = request(messageId, Map.of("temperature_c", new BigDecimal("23.7")));
        var second = request(messageId, Map.of("temperature_c", new BigDecimal("24.1")));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<SensorIngestOrchestrator.IngestOutcome>> tasks = List.of(
                    () -> orchestrator.ingest("producer-concurrent", first),
                    () -> orchestrator.ingest("producer-concurrent", second)
            );
            List<Future<SensorIngestOrchestrator.IngestOutcome>> futures = executor.invokeAll(tasks);
            List<SensorIngestOrchestrator.IngestOutcome> outcomes = new ArrayList<>();
            for (Future<SensorIngestOrchestrator.IngestOutcome> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(outcomes).extracting(outcome -> outcome.response().code())
                    .containsExactlyInAnyOrder("MEASUREMENT_INGESTED", "IDEMPOTENCY_CONFLICT");
            assertThat(metadataRepo.findByProducerIdAndSensor_ExternalIdAndMessageId(
                    "producer-concurrent", sensorId, messageId)).isPresent();
            assertThat(medicaoRepo.count()).isGreaterThanOrEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void preservesMockIngestEndpoint() throws Exception {
        mvc.perform(post("/api/sensors/ingest/mock")
                        .with(user("ingestor-test").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private SensorIngestDTOs.CanonicalIngestRequest request(String messageId, Map<String, Object> values) {
        return new SensorIngestDTOs.CanonicalIngestRequest(
                messageId,
                sensorId,
                Instant.parse("2026-08-11T23:30:00Z"),
                MedicaoIngestaoSource.MQTT,
                Instant.parse("2026-08-11T23:30:02Z"),
                values
        );
    }
}
