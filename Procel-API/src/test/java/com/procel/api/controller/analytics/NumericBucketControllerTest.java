package com.procel.api.controller.analytics;

import com.procel.api.entity.rooms.Campus;
import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.rooms.Predio;
import com.procel.api.entity.rooms.Unidade;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.Sensor;
import com.procel.api.entity.sensors.TipoDeSensor;
import com.procel.api.repository.rooms.CampusRepository;
import com.procel.api.repository.rooms.CompartimentoRepository;
import com.procel.api.repository.rooms.PredioRepository;
import com.procel.api.repository.rooms.UnidadeRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.repository.sensors.SensorRepository;
import com.procel.api.repository.sensors.TipoDeSensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NumericBucketControllerTest {
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
        registry.add("procel.analytics.aggregation.worker-enabled", () -> "false");
        registry.add("procel.analytics.buckets.max-period", () -> "30d");
        registry.add("procel.analytics.buckets.max-page-size", () -> "2");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CampusRepository campusRepository;
    @Autowired UnidadeRepository unidadeRepository;
    @Autowired PredioRepository predioRepository;
    @Autowired CompartimentoRepository compartimentoRepository;
    @Autowired TipoDeSensorRepository tipoDeSensorRepository;
    @Autowired SensorRepository sensorRepository;
    @Autowired ParametroDefRepository parametroDefRepository;

    private String sensorId;
    private String otherSensorId;
    private String compartimentoId;
    private String otherCompartimentoId;
    private UUID parametroDefId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from analytics_numeric_bucket");

        String suffix = UUID.randomUUID().toString();
        Campus campus = campusRepository.save(new Campus("Campus buckets " + suffix));
        Unidade unidade = unidadeRepository.save(new Unidade("Unidade buckets " + suffix));
        Predio predio = predioRepository.save(new Predio(campus, "Predio buckets " + suffix));

        compartimentoId = "ROOM-BUCKETS-" + suffix;
        Compartimento compartimento = compartimentoRepository.save(
                new Compartimento(compartimentoId, predio, unidade, "Sala buckets " + suffix, "Sala"));
        otherCompartimentoId = "ROOM-BUCKETS-OTHER-" + suffix;
        Compartimento otherCompartimento = compartimentoRepository.save(
                new Compartimento(otherCompartimentoId, predio, unidade, "Sala buckets other " + suffix, "Sala"));

        TipoDeSensor tipo = tipoDeSensorRepository.save(new TipoDeSensor("TYPE-BUCKETS-" + suffix));
        ParametroDef parametro = parametroDefRepository.save(
                new ParametroDef(tipo, "temperature-" + suffix, "Temperature", DataType.NUMERIC, "C"));
        parametroDefId = parametro.getId();

        sensorId = "SENSOR-BUCKETS-" + suffix;
        sensorRepository.save(new Sensor(sensorId, "Sensor buckets " + suffix, tipo, compartimento));
        otherSensorId = "SENSOR-BUCKETS-OTHER-" + suffix;
        sensorRepository.save(new Sensor(otherSensorId, "Sensor buckets other " + suffix, tipo, otherCompartimento));

        insertBucket(sensorId, compartimentoId, "2026-08-19T00:00:00Z", "2026-08-19T00:05:00Z",
                "10.123456", "8.000000", "12.000000", 1, 1);
        insertBucket(sensorId, compartimentoId, "2026-08-19T00:05:00Z", "2026-08-19T00:10:00Z",
                "20.000000", "18.000000", "22.000000", 3, 1);
        insertBucket(sensorId, compartimentoId, "2026-08-19T00:05:00Z", "2026-08-19T00:15:00Z",
                "30.000000", "29.000000", "31.000000", 1, 2);
        insertBucket(otherSensorId, otherCompartimentoId, "2026-08-19T00:05:00Z", "2026-08-19T00:10:00Z",
                "40.000000", "39.000000", "41.000000", 1, 1);
    }

    @Test
    void listsPersistedBucketsWithPaginationAndStableOrdering() throws Exception {
        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].sensorExternalId").value(sensorId))
                .andExpect(jsonPath("$.content[0].sensorNome").exists())
                .andExpect(jsonPath("$.content[0].parametroDefId").value(parametroDefId.toString()))
                .andExpect(jsonPath("$.content[0].parametroNome").exists())
                .andExpect(jsonPath("$.content[0].unidade").value("C"))
                .andExpect(jsonPath("$.content[0].compartimentoId").value(compartimentoId))
                .andExpect(jsonPath("$.content[0].bucketStart").value("2026-08-19T00:00:00Z"))
                .andExpect(jsonPath("$.content[0].bucketEnd").value("2026-08-19T00:05:00Z"))
                .andExpect(jsonPath("$.content[0].averageValue").value(10.123456))
                .andExpect(jsonPath("$.content[1].sensorExternalId").value(sensorId));
    }

    @Test
    void returnsEmptyPagePastTotalAndForExistingButIncompatibleFilters() throws Exception {
        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("sensorExternalId", sensorId)
                        .param("compartimentoId", otherCompartimentoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void appliesAllFiltersAndPeriodBoundaryRules() throws Exception {
        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("operator").roles("OPERADOR"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("sensorExternalId", sensorId)
                        .param("parametroDefId", parametroDefId.toString())
                        .param("compartimentoId", compartimentoId)
                        .param("aggregationVersion", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].bucketStart").value("2026-08-19T00:00:00Z"))
                .andExpect(jsonPath("$.content[1].bucketEnd").value("2026-08-19T00:10:00Z"));

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("operator").roles("OPERADOR"))
                        .param("from", "2026-08-19T00:01:00Z")
                        .param("to", "2026-08-19T00:09:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void summarizesWithWeightedAverageFromPersistedBuckets() throws Exception {
        mvc.perform(get("/api/analytics/numeric-buckets/summary")
                        .with(user("admin").roles("ADMIN"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("sensorExternalId", sensorId)
                        .param("parametroDefId", parametroDefId.toString())
                        .param("compartimentoId", compartimentoId)
                        .param("aggregationVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].averageValue").value(17.530864))
                .andExpect(jsonPath("$[0].minimumValue").value(8.000000))
                .andExpect(jsonPath("$[0].maximumValue").value(22.000000))
                .andExpect(jsonPath("$[0].sampleCount").value(4))
                .andExpect(jsonPath("$[0].bucketCount").value(2));
    }

    @Test
    void rejectsInvalidFiltersAndMissingReferencesWithStructuredErrors() throws Exception {
        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("to", "2026-08-19T00:10:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:10:00Z")
                        .param("to", "2026-08-19T00:00:00Z"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("size", "3"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("aggregationVersion", "0"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("sensorExternalId", "missing"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("UNPROCESSABLE_ENTITY"));

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("parametroDefId", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("analyst").roles("ANALISTA"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z")
                        .param("compartimentoId", "missing"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deniesUsuarioAndIngestorAndPublishesOpenApiOperations() throws Exception {
        mvc.perform(get("/api/analytics/numeric-buckets")
                        .with(user("user").roles("USUARIO"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/analytics/numeric-buckets/summary")
                        .with(user("ingestor").roles("INGESTOR"))
                        .param("from", "2026-08-19T00:00:00Z")
                        .param("to", "2026-08-19T00:10:00Z"))
                .andExpect(status().isForbidden());

        String openApi = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(openApi).contains("/api/analytics/numeric-buckets");
        assertThat(openApi).contains("/api/analytics/numeric-buckets/summary");
    }

    private void insertBucket(
            String bucketSensorId,
            String bucketCompartimentoId,
            String start,
            String end,
            String average,
            String minimum,
            String maximum,
            long sampleCount,
            int aggregationVersion
    ) {
        jdbcTemplate.update("""
                insert into analytics_numeric_bucket
                (sensor_external_id, parametro_def_id, compartimento_id, bucket_start, bucket_end,
                 aggregation_version, average_value, minimum_value, maximum_value, sample_count,
                 created_at, updated_at)
                values (?, ?, ?, ?::timestamptz, ?::timestamptz, ?, ?, ?, ?, ?, now(), now())
                """,
                bucketSensorId,
                parametroDefId,
                bucketCompartimentoId,
                start,
                end,
                aggregationVersion,
                new BigDecimal(average),
                new BigDecimal(minimum),
                new BigDecimal(maximum),
                sampleCount);
    }
}
