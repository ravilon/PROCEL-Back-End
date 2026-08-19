package com.procel.api.controller.sensors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.entity.rooms.*;
import com.procel.api.entity.sensors.*;
import com.procel.api.repository.rooms.*;
import com.procel.api.repository.sensors.*;
import com.procel.api.service.sensors.ParametroQualificacaoService;
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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SensorTelemetryInternalIngestControllerTest {
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
    @Autowired CampusRepository campusRepo;
    @Autowired UnidadeRepository unidadeRepo;
    @Autowired PredioRepository predioRepo;
    @Autowired CompartimentoRepository compartimentoRepo;
    @Autowired TipoDeSensorRepository tipoRepo;
    @Autowired SensorRepository sensorRepo;
    @Autowired ParametroDefRepository parametroRepo;
    @Autowired SensorIntegrationProfileRepository profileRepo;
    @Autowired SensorIntegrationParserVersionRepository versionRepo;
    @Autowired SensorIntegrationBindingRepository bindingRepo;
    @Autowired MedicaoIngestaoMetadataRepository metadataRepo;
    @MockitoBean ParametroQualificacaoService qualificacaoService;

    private String sensorId;

    @BeforeEach
    void setUp() {
        sensorId = "SII-TELEMETRY-" + UUID.randomUUID();
        Campus campus = campusRepo.save(new Campus("Campus " + sensorId));
        Unidade unidade = unidadeRepo.save(new Unidade("Unidade " + sensorId));
        Predio predio = predioRepo.save(new Predio(campus, "Predio " + sensorId));
        Compartimento compartimento = compartimentoRepo.save(
                new Compartimento("ROOM-" + sensorId, predio, unidade, "Sala " + sensorId, "Sala"));
        TipoDeSensor tipo = tipoRepo.save(new TipoDeSensor("TYPE-" + sensorId));
        parametroRepo.save(new ParametroDef(tipo, "temperature_c", "Temperatura", DataType.NUMERIC, "C"));
        sensorRepo.save(new Sensor(sensorId, "Sensor " + sensorId, tipo, compartimento));
    }

    @Test
    void internalRouteIsRestrictedToTelemetryServiceRole() throws Exception {
        var context = activeProfile(SensorResolutionMode.PAYLOAD_POINTER);
        String url = "/api/sensors/internal/telemetry-events/ingest/integrations/" + context.profile().getId();
        String body = telemetryBody("raw-sec", "original-producer", "raw-sec-event", "24.1");

        mvc.perform(post(url).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(user("ingestor").roles("INGESTOR")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(user("telemetry-service").roles("TELEMETRY_SERVICE")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MEASUREMENT_INGESTED"));
    }

    @Test
    void internalIngestStoresRawAuditContextAndPublicIngestLeavesItNull() throws Exception {
        var context = activeProfile(SensorResolutionMode.PAYLOAD_POINTER);

        mvc.perform(post("/api/sensors/ingest/integrations/" + context.profile().getId())
                        .with(user("public-producer").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(payload("parsed-public", "22.1")))
                .andExpect(status().isCreated());
        var publicMetadata = metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndMessageId(
                context.profile().getId(), sensorId, "parsed-public").orElseThrow();
        assertThat(publicMetadata.getOriginalProducerId()).isNull();
        assertThat(publicMetadata.getRawMessageId()).isNull();
        assertThat(publicMetadata.getRawTelemetryEventId()).isNull();
        assertThat(publicMetadata.getRawReceivedAt()).isNull();
        assertThat(publicMetadata.getRawSourceTimestamp()).isNull();

        mvc.perform(post("/api/sensors/internal/telemetry-events/ingest/integrations/" + context.profile().getId())
                        .with(user("telemetry-service").roles("TELEMETRY_SERVICE"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(telemetryBody("raw-audit", "device-producer", "raw-event-audit", "23.7")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value("raw-audit"));

        var metadata = metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndOriginalProducerIdAndRawMessageId(
                context.profile().getId(), sensorId, "device-producer", "raw-audit").orElseThrow();
        assertThat(metadata.getProducerId()).isEqualTo("telemetry-service");
        assertThat(metadata.getOriginalProducerId()).isEqualTo("device-producer");
        assertThat(metadata.getMessageId()).isEqualTo("raw-audit");
        assertThat(metadata.getRawMessageId()).isEqualTo("raw-audit");
        assertThat(metadata.getRawTelemetryEventId()).isEqualTo("raw-event-audit");
        assertThat(metadata.getRawReceivedAt()).isEqualTo(Instant.parse("2026-08-12T10:00:00Z"));
        assertThat(metadata.getRawSourceTimestamp()).isEqualTo(Instant.parse("2026-08-12T09:59:58Z"));
        assertThat(metadata.getIntegrationProfileId()).isEqualTo(context.profile().getId());
        assertThat(metadata.getParserVersionId()).isEqualTo(context.version().getId());
    }

    @Test
    void internalIdempotencyUsesOriginalProducerAndRawMessage() throws Exception {
        var context = activeProfile(SensorResolutionMode.PAYLOAD_POINTER);
        String url = "/api/sensors/internal/telemetry-events/ingest/integrations/" + context.profile().getId();

        mvc.perform(post(url).with(user("telemetry-service").roles("TELEMETRY_SERVICE")).with(csrf())
                        .contentType("application/json")
                        .content(telemetryBody("raw-shared", "producer-a", "raw-event-a1", "23.7")))
                .andExpect(status().isCreated());
        mvc.perform(post(url).with(user("telemetry-service").roles("TELEMETRY_SERVICE")).with(csrf())
                        .contentType("application/json")
                        .content(telemetryBody("raw-shared", "producer-a", "raw-event-a2", "23.70")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MESSAGE"));
        mvc.perform(post(url).with(user("telemetry-service").roles("TELEMETRY_SERVICE")).with(csrf())
                        .contentType("application/json")
                        .content(telemetryBody("raw-shared", "producer-b", "raw-event-b1", "23.7")))
                .andExpect(status().isCreated());
        mvc.perform(post(url).with(user("telemetry-service").roles("TELEMETRY_SERVICE")).with(csrf())
                        .contentType("application/json")
                        .content(telemetryBody("raw-shared", "producer-a", "raw-event-a3", "24.1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        assertThat(metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndOriginalProducerIdAndRawMessageId(
                context.profile().getId(), sensorId, "producer-a", "raw-shared")).isPresent();
        assertThat(metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndOriginalProducerIdAndRawMessageId(
                context.profile().getId(), sensorId, "producer-b", "raw-shared")).isPresent();
    }

    @Test
    void routeSensorModeUsesInternalRouteSensorAndRejectsPayloadPointerRoute() throws Exception {
        var routeContext = activeProfile(SensorResolutionMode.ROUTE_SENSOR);
        mvc.perform(post("/api/sensors/internal/telemetry-events/" + sensorId + "/ingest/integrations/" + routeContext.profile().getId())
                        .with(user("telemetry-service").roles("TELEMETRY_SERVICE"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(telemetryBodyWithoutPayloadSensor("raw-route", "producer-route", "raw-event-route", "23.7")))
                .andExpect(status().isCreated());

        var payloadContext = activeProfile(SensorResolutionMode.PAYLOAD_POINTER);
        mvc.perform(post("/api/sensors/internal/telemetry-events/" + sensorId + "/ingest/integrations/" + payloadContext.profile().getId())
                        .with(user("telemetry-service").roles("TELEMETRY_SERVICE"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(telemetryBody("raw-payload-route-conflict", "producer-route", "raw-event-route-conflict", "23.7")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SENSOR_RESOLUTION_CONFLICT"));
    }

    @Test
    void concurrentEquivalentAndDivergentInternalIngestionsHaveOneWinner() throws Exception {
        var context = activeProfile(SensorResolutionMode.PAYLOAD_POINTER);
        String url = "/api/sensors/internal/telemetry-events/ingest/integrations/" + context.profile().getId();
        String rawMessageId = "raw-race-" + UUID.randomUUID();

        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = List.of(
                    () -> postTelemetry(url, rawMessageId, "producer-race", "race-event-1", "23.7"),
                    () -> postTelemetry(url, rawMessageId, "producer-race", "race-event-2", "23.70"),
                    () -> postTelemetry(url, rawMessageId, "producer-race", "race-event-3", "24.1"),
                    () -> postTelemetry(url, rawMessageId, "producer-race", "race-event-4", "23.7"),
                    () -> postTelemetry(url, rawMessageId, "producer-race", "race-event-5", "24.1"),
                    () -> postTelemetry(url, rawMessageId, "producer-race", "race-event-6", "23.70")
            );
            var futures = executor.invokeAll(tasks);

            int created = 0;
            int duplicate = 0;
            int conflict = 0;
            for (var future : futures) {
                String result = future.get(10, TimeUnit.SECONDS);
                if ("MEASUREMENT_INGESTED".equals(result)) created++;
                if ("DUPLICATE_MESSAGE".equals(result)) duplicate++;
                if ("IDEMPOTENCY_CONFLICT".equals(result)) conflict++;
            }

            assertThat(created).isEqualTo(1);
            assertThat(duplicate).isGreaterThanOrEqualTo(1);
            assertThat(conflict).isGreaterThanOrEqualTo(1);
            assertThat(metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndOriginalProducerIdAndRawMessageId(
                    context.profile().getId(), sensorId, "producer-race", rawMessageId)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private String postTelemetry(
            String url,
            String rawMessageId,
            String originalProducerId,
            String rawTelemetryEventId,
            String temperature
    ) throws Exception {
        String body = telemetryBody(rawMessageId, originalProducerId, rawTelemetryEventId, temperature);
        String response = mvc.perform(post(url)
                        .with(user("telemetry-service").roles("TELEMETRY_SERVICE"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("code").asText();
    }

    private ActiveContext activeProfile(SensorResolutionMode mode) {
        var profile = profileRepo.save(new SensorIntegrationProfile("Profile " + UUID.randomUUID(), null, MedicaoIngestaoSource.REST));
        var version = new SensorIntegrationParserVersion(
                profile,
                versionRepo.maxVersionByProfile(profile.getId()) + 1,
                mode,
                "/meta/id",
                mode == SensorResolutionMode.PAYLOAD_POINTER ? "/device/id" : null,
                "/measuredAt",
                "/receivedAt"
        );
        version.replaceMappings(List.of(
                new SensorIntegrationValueMapping("temperature_c", "/readings/temperature", true)
        ));
        version.activate(Instant.now());
        version = versionRepo.save(version);
        bindingRepo.save(new SensorIntegrationBinding(sensorRepo.findById(sensorId).orElseThrow(), profile));
        return new ActiveContext(profile, version);
    }

    private String telemetryBody(String rawMessageId, String originalProducerId, String rawTelemetryEventId, String temperature) {
        return """
                {
                  "rawTelemetryEventId":"%s",
                  "originalProducerId":"%s",
                  "rawMessageId":"%s",
                  "rawReceivedAt":"2026-08-12T10:00:00Z",
                  "rawSourceTimestamp":"2026-08-12T09:59:58Z",
                  "payload":%s
                }
                """.formatted(rawTelemetryEventId, originalProducerId, rawMessageId, payload("parsed-" + rawMessageId, temperature));
    }

    private String telemetryBodyWithoutPayloadSensor(
            String rawMessageId,
            String originalProducerId,
            String rawTelemetryEventId,
            String temperature
    ) {
        return """
                {
                  "rawTelemetryEventId":"%s",
                  "originalProducerId":"%s",
                  "rawMessageId":"%s",
                  "rawReceivedAt":"2026-08-12T10:00:00Z",
                  "rawSourceTimestamp":"2026-08-12T09:59:58Z",
                  "payload":{
                    "meta":{"id":"parsed-%s"},
                    "measuredAt":"2026-08-11T23:30:00Z",
                    "receivedAt":"2026-08-11T23:30:02Z",
                    "readings":{"temperature":%s}
                  }
                }
                """.formatted(rawTelemetryEventId, originalProducerId, rawMessageId, rawMessageId, temperature);
    }

    private String payload(String messageId, String temperature) {
        return """
                {
                  "meta":{"id":"%s"},
                  "device":{"id":"%s"},
                  "measuredAt":"2026-08-11T23:30:00Z",
                  "receivedAt":"2026-08-11T23:30:02Z",
                  "readings":{"temperature":%s}
                }
                """.formatted(messageId, sensorId, temperature);
    }

    private record ActiveContext(SensorIntegrationProfile profile, SensorIntegrationParserVersion version) {}
}
