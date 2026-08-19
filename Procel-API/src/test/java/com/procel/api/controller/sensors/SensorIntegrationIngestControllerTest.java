package com.procel.api.controller.sensors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.entity.rooms.*;
import com.procel.api.entity.sensors.*;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.repository.rooms.*;
import com.procel.api.repository.sensors.*;
import com.procel.api.service.sensors.ParametroQualificacaoService;
import com.procel.api.service.sensors.SensorCanonicalIngestionTransaction;
import com.procel.api.service.sensors.SensorExternalPayloadParser;
import com.procel.api.service.sensors.SensorIntegrationActivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SensorIntegrationIngestControllerTest {
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
    @Autowired MedicaoRepository medicaoRepo;
    @Autowired ParametroValorRepository parametroValorRepo;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired SensorIntegrationActivationService activationService;
    @Autowired SensorExternalPayloadParser parser;
    @Autowired SensorCanonicalIngestionTransaction ingestionTransaction;
    @MockitoBean ParametroQualificacaoService qualificacaoService;

    private String sensorId;

    @BeforeEach
    void setUp() {
        sensorId = "SII-INTEGRATION-" + UUID.randomUUID();
        Campus campus = campusRepo.save(new Campus("Campus " + sensorId));
        Unidade unidade = unidadeRepo.save(new Unidade("Unidade " + sensorId));
        Predio predio = predioRepo.save(new Predio(campus, "Predio " + sensorId));
        Compartimento compartimento = compartimentoRepo.save(
                new Compartimento("ROOM-" + sensorId, predio, unidade, "Sala " + sensorId, "Sala"));
        TipoDeSensor tipo = tipoRepo.save(new TipoDeSensor("TYPE-" + sensorId));
        parametroRepo.save(new ParametroDef(tipo, "temperature_c", "Temperatura", DataType.NUMERIC, "C"));
        parametroRepo.save(new ParametroDef(tipo, "presence", "Presenca", DataType.BOOLEAN, null));
        sensorRepo.save(new Sensor(sensorId, "Sensor " + sensorId, tipo, compartimento));
    }

    @Test
    void adminContractsCreateVersionActivateBindingAndSnapshot() throws Exception {
        String profile = mvc.perform(post("/api/sensor-integrations/profiles")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"nome":"Profile API","descricao":"REST","source":"REST"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("REST"))
                .andReturn().getResponse().getContentAsString();
        String profileId = objectMapper.readTree(profile).get("id").asText();

        String version = mvc.perform(post("/api/sensor-integrations/profiles/" + profileId + "/versions")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(versionBody(SensorResolutionMode.PAYLOAD_POINTER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String versionId = objectMapper.readTree(version).get("id").asText();

        mvc.perform(get("/api/sensor-integrations/profiles/" + profileId + "/versions")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(versionId));

        mvc.perform(get("/api/sensor-integrations/profiles/" + profileId + "/versions/" + versionId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId));

        mvc.perform(post("/api/sensor-integrations/profiles/" + profileId + "/versions/" + versionId + "/activate")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"expectedActiveVersionId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/api/sensor-integrations/profiles/" + profileId + "/bindings")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"sensorExternalId\":\"" + sensorId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true));

        mvc.perform(get("/api/sensor-integrations/profiles/" + profileId + "/bindings")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sensorExternalId").value(sensorId));

        String snapshot = mvc.perform(get("/api/sensor-integrations/snapshot")
                        .with(user("ingestor").roles("INGESTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/sensor-integrations/snapshot")
                        .with(user("telemetry-service").roles("TELEMETRY_SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(objectMapper.readTree(snapshot).get("profiles"))
                .anySatisfy(node -> {
                    assertThat(node.get("id").asText()).isEqualTo(profileId);
                    assertThat(node.get("activeParserVersion").get("id").asText()).isEqualTo(versionId);
                });
    }

    @Test
    void integrationIngestPersistsProfileContextAndUsesProfileIdempotencyAcrossProducers() throws Exception {
        var context = activeProfile(SensorResolutionMode.PAYLOAD_POINTER);
        String body = payload("msg-profile", "23.70");

        mvc.perform(post("/api/sensors/ingest/integrations/" + context.profile().getId())
                        .with(user("producer-a").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MEASUREMENT_INGESTED"));

        mvc.perform(post("/api/sensors/ingest/integrations/" + context.profile().getId())
                        .with(user("producer-b").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MESSAGE"));

        var metadata = metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndMessageId(
                context.profile().getId(), sensorId, "msg-profile").orElseThrow();
        assertThat(metadata.getIntegrationProfileId()).isEqualTo(context.profile().getId());
        assertThat(metadata.getParserVersionId()).isEqualTo(context.version().getId());
    }

    @Test
    void profileIngestRejectsDivergentDuplicateAndInactiveReferences() throws Exception {
        var context = activeProfile(SensorResolutionMode.PAYLOAD_POINTER);
        mvc.perform(post("/api/sensors/ingest/integrations/" + context.profile().getId())
                        .with(user("producer-a").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(payload("msg-conflict", "23.7")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/sensors/ingest/integrations/" + context.profile().getId())
                        .with(user("producer-b").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(payload("msg-conflict", "24.1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        context.profile().deactivate();
        profileRepo.save(context.profile());
        mvc.perform(post("/api/sensors/ingest/integrations/" + context.profile().getId())
                        .with(user("producer-a").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(payload("msg-inactive", "24.1")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("PROFILE_INACTIVE"));
    }

    @Test
    void replacedParserVersionBetweenParsingAndReservationDoesNotPersistAnything() throws Exception {
        var context = activeProfile(SensorResolutionMode.PAYLOAD_POINTER);
        var canonical = parser.parse(
                objectMapper.readTree(payload("msg-version-replaced", "23.70")),
                context.version(),
                context.profile().getSource(),
                null
        );

        var replacement = draft(context.profile());
        activationService.activateVersion(context.profile().getId(), replacement.getId(), context.version().getId());

        long metadataBefore = metadataRepo.count();
        long medicaoBefore = medicaoRepo.count();
        long valorBefore = parametroValorRepo.count();

        assertThatThrownBy(() -> ingestionTransaction.ingestProfileNew(
                context.profile().getId(),
                context.version().getId(),
                "producer-version-race",
                canonical
        ))
                .isInstanceOfSatisfying(ApiStatusException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(ex.getError()).isEqualTo("PARSER_VERSION_CHANGED");
                });

        assertThat(metadataRepo.count()).isEqualTo(metadataBefore);
        assertThat(medicaoRepo.count()).isEqualTo(medicaoBefore);
        assertThat(parametroValorRepo.count()).isEqualTo(valorBefore);
        assertThat(metadataRepo.findByIntegrationProfileIdAndSensor_ExternalIdAndMessageId(
                context.profile().getId(),
                sensorId,
                "msg-version-replaced"
        )).isEmpty();
    }

    @Test
    void directIngestAndMockEndpointArePreserved() throws Exception {
        mvc.perform(post("/api/sensors/ingest")
                        .with(user("direct-producer").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new com.procel.api.dto.sensors.SensorIngestDTOs.CanonicalIngestRequest(
                                "direct-msg",
                                sensorId,
                                Instant.parse("2026-08-11T23:30:00Z"),
                                MedicaoIngestaoSource.API,
                                null,
                                java.util.Map.of("temperature_c", new BigDecimal("22.1"))
                        ))))
                .andExpect(status().isCreated());

        var metadata = metadataRepo.findByProducerIdAndSensor_ExternalIdAndMessageId("direct-producer", sensorId, "direct-msg").orElseThrow();
        assertThat(metadata.getIntegrationProfileId()).isNull();
        assertThat(metadata.getParserVersionId()).isNull();

        mvc.perform(post("/api/sensors/ingest/mock")
                        .with(user("mock-producer").roles("INGESTOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void activationConcurrencyAllowsOnlyOneWinner() throws Exception {
        var profile = profileRepo.save(new SensorIntegrationProfile("Profile " + UUID.randomUUID(), null, MedicaoIngestaoSource.REST));
        var v1 = draft(profile);
        var v2 = draft(profile);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = executor.invokeAll(List.of(
                    () -> activationService.activateVersion(profile.getId(), v1.getId(), null),
                    () -> activationService.activateVersion(profile.getId(), v2.getId(), null)
            ));
            int success = 0;
            int conflict = 0;
            for (var future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    success++;
                } catch (Exception ex) {
                    conflict++;
                }
            }
            assertThat(success).isEqualTo(1);
            assertThat(conflict).isEqualTo(1);
            assertThat(versionRepo.findByProfile_IdAndStatus(profile.getId(), SensorIntegrationParserStatus.ACTIVE)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseConstraintsProtectStateConsistency() {
        assertThatThrownBy(() -> {
            UUID profileId = UUID.randomUUID();
            jdbcTemplate.update("""
                    insert into sensor_integration_profile (id, nome, source, ativo)
                    values (?, 'bad-pub', 'REST', true)
                    """, profileId);
            jdbcTemplate.update("""
                    insert into sensor_integration_parser_version
                    (id, profile_id, version, status, sensor_resolution_mode, message_id_pointer, timestamp_pointer, timestamp_format)
                    values (gen_random_uuid(), ?, 1, 'ACTIVE', 'ROUTE_SENSOR', '/id', '/ts', 'ISO_INSTANT')
                    """, profileId);
        }).isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> {
            var profile = profileRepo.save(new SensorIntegrationProfile("bad-binding-" + UUID.randomUUID(), null, MedicaoIngestaoSource.REST));
            jdbcTemplate.update("""
                    insert into sensor_integration_binding (id, sensor_external_id, profile_id, ativo, deactivated_at)
                    values (gen_random_uuid(), ?, ?, false, null)
                    """, sensorId, profile.getId());
        }).isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> {
            var firstProfile = profileRepo.save(new SensorIntegrationProfile("fk-profile-a-" + UUID.randomUUID(), null, MedicaoIngestaoSource.REST));
            var secondProfile = profileRepo.save(new SensorIntegrationProfile("fk-profile-b-" + UUID.randomUUID(), null, MedicaoIngestaoSource.REST));
            var version = draft(firstProfile);
            version.activate(Instant.now());
            versionRepo.save(version);
            jdbcTemplate.update("""
                    insert into medicao_ingestao_metadata
                    (producer_id, sensor_external_id, message_id, source, api_received_at, payload_fingerprint, status,
                     integration_profile_id, parser_version_id)
                    values ('producer', ?, 'fk-mismatch', 'REST', now(), repeat('c', 64), 'PROCESSING', ?, ?)
                    """, sensorId, secondProfile.getId(), version.getId());
        }).isInstanceOf(RuntimeException.class);
    }

    private ActiveContext activeProfile(SensorResolutionMode mode) {
        var profile = profileRepo.save(new SensorIntegrationProfile("Profile " + UUID.randomUUID(), null, MedicaoIngestaoSource.REST));
        var version = draft(profile, mode);
        version.activate(Instant.now());
        version = versionRepo.save(version);
        bindingRepo.save(new SensorIntegrationBinding(sensorRepo.findById(sensorId).orElseThrow(), profile));
        return new ActiveContext(profile, version);
    }

    private SensorIntegrationParserVersion draft(SensorIntegrationProfile profile) {
        return draft(profile, SensorResolutionMode.PAYLOAD_POINTER);
    }

    private SensorIntegrationParserVersion draft(SensorIntegrationProfile profile, SensorResolutionMode mode) {
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
                new SensorIntegrationValueMapping("temperature_c", "/readings/temperature", true),
                new SensorIntegrationValueMapping("presence", "/readings/presence", false)
        ));
        return versionRepo.save(version);
    }

    private String versionBody(SensorResolutionMode mode) {
        String sensorPointer = mode == SensorResolutionMode.PAYLOAD_POINTER
                ? "\"sensorExternalIdPointer\":\"/device/id\","
                : "";
        return """
                {
                  "sensorResolutionMode":"%s",
                  "messageIdPointer":"/meta/id",
                  %s
                  "timestampPointer":"/measuredAt",
                  "sourceReceivedAtPointer":"/receivedAt",
                  "timestampFormat":"ISO_INSTANT",
                  "valueMappings":[
                    {"parameterName":"temperature_c","valuePointer":"/readings/temperature","required":true},
                    {"parameterName":"presence","valuePointer":"/readings/presence","required":false}
                  ]
                }
                """.formatted(mode.name(), sensorPointer);
    }

    private String payload(String messageId, String temperature) {
        return """
                {
                  "meta":{"id":"%s"},
                  "device":{"id":"%s"},
                  "measuredAt":"2026-08-11T23:30:00Z",
                  "receivedAt":"2026-08-11T23:30:02Z",
                  "readings":{"temperature":%s,"presence":true}
                }
                """.formatted(messageId, sensorId, temperature);
    }

    private record ActiveContext(SensorIntegrationProfile profile, SensorIntegrationParserVersion version) {}
}
