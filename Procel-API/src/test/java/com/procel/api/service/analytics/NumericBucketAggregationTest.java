package com.procel.api.service.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.entity.rooms.Campus;
import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.rooms.Predio;
import com.procel.api.entity.rooms.Unidade;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.ParametroValor;
import com.procel.api.entity.sensors.Sensor;
import com.procel.api.entity.sensors.TipoDeSensor;
import com.procel.api.repository.rooms.CampusRepository;
import com.procel.api.repository.rooms.CompartimentoRepository;
import com.procel.api.repository.rooms.PredioRepository;
import com.procel.api.repository.rooms.UnidadeRepository;
import com.procel.api.repository.sensors.MedicaoRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.repository.sensors.ParametroValorRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NumericBucketAggregationTest {
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
        registry.add("procel.analytics.aggregation.min-window", () -> "1s");
        registry.add("procel.analytics.aggregation.max-interval", () -> "30d");
        registry.add("procel.analytics.aggregation.max-window", () -> "7d");
        registry.add("procel.analytics.aggregation.max-windows", () -> "1000");
        registry.add("procel.analytics.aggregation.lease-timeout", () -> "1s");
        registry.add("procel.analytics.aggregation.backoff", () -> "1s");
        registry.add("procel.analytics.aggregation.max-attempts", () -> "2");
        registry.add("procel.analytics.aggregation.aggregation-version", () -> "1");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AggregationJobWorker worker;
    @Autowired CanonicalMeasurementWindowScanner processor;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CampusRepository campusRepository;
    @Autowired UnidadeRepository unidadeRepository;
    @Autowired PredioRepository predioRepository;
    @Autowired CompartimentoRepository compartimentoRepository;
    @Autowired TipoDeSensorRepository tipoRepository;
    @Autowired SensorRepository sensorRepository;
    @Autowired ParametroDefRepository parametroDefRepository;
    @Autowired MedicaoRepository medicaoRepository;
    @Autowired ParametroValorRepository parametroValorRepository;

    private String roomA;
    private String roomB;
    private Sensor sensorA;
    private Sensor sensorB;
    private ParametroDef temperature;
    private ParametroDef humidity;
    private ParametroDef presence;
    private ParametroDef label;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from analytics_numeric_bucket");
        jdbcTemplate.update("delete from analytics_aggregation_window");
        jdbcTemplate.update("delete from analytics_aggregation_job");

        String suffix = UUID.randomUUID().toString();
        Campus campus = campusRepository.save(new Campus("Campus bucket " + suffix));
        Unidade unidade = unidadeRepository.save(new Unidade("Unidade bucket " + suffix));
        Predio predio = predioRepository.save(new Predio(campus, "Predio bucket " + suffix));
        roomA = "ROOM-BUCKET-A-" + suffix;
        roomB = "ROOM-BUCKET-B-" + suffix;
        Compartimento compartimentoA = compartimentoRepository.save(new Compartimento(roomA, predio, unidade, "Sala A " + suffix, "Sala"));
        Compartimento compartimentoB = compartimentoRepository.save(new Compartimento(roomB, predio, unidade, "Sala B " + suffix, "Sala"));
        TipoDeSensor tipo = tipoRepository.save(new TipoDeSensor("TYPE-BUCKET-" + suffix));
        temperature = parametroDefRepository.save(new ParametroDef(tipo, "temperature_c", "Temperatura", DataType.NUMERIC, "C"));
        humidity = parametroDefRepository.save(new ParametroDef(tipo, "humidity", "Umidade", DataType.NUMERIC, "%"));
        presence = parametroDefRepository.save(new ParametroDef(tipo, "presence", "Presenca", DataType.BOOLEAN, null));
        label = parametroDefRepository.save(new ParametroDef(tipo, "label", "Rotulo", DataType.TEXT, null));
        sensorA = sensorRepository.save(new Sensor("SENSOR-BUCKET-A-" + suffix, "Sensor A " + suffix, tipo, compartimentoA));
        sensorB = sensorRepository.save(new Sensor("SENSOR-BUCKET-B-" + suffix, "Sensor B " + suffix, tipo, compartimentoB));
    }

    @Test
    void computesAverageMinimumMaximumCountAndDecimalPrecision() throws Exception {
        measurement(sensorA, "2026-08-19T00:00:00Z", temperature, "1.111111", null, null);
        measurement(sensorA, "2026-08-19T00:02:00Z", temperature, "2.222222", null, null);
        measurement(sensorA, "2026-08-19T00:04:00Z", temperature, "2.333333", null, null);

        JsonNode job = createJob("2026-08-19T00:00:00Z", "2026-08-19T00:05:00Z", "PT5M", sensorA.getExternalId(), null);
        UUID windowId = UUID.fromString(job.get("windows").get(0).get("id").asText());
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(windowStatus(windowId)).describedAs(windowError(windowId)).isEqualTo("COMPLETED");

        assertBucket(sensorA.getExternalId(), temperature.getId(), "2026-08-19T00:00:00Z", "2026-08-19T00:05:00Z",
                "1.888889", "1.111111", "2.333333", 3);
        mvc.perform(get("/api/analytics/aggregation-jobs/" + job.get("id").asText())
                        .with(user("analyst").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void usesSemiOpenIntervalIncludingStartAndExcludingEnd() throws Exception {
        measurement(sensorA, "2026-08-19T01:00:00Z", temperature, "10.000000", null, null);
        measurement(sensorA, "2026-08-19T01:05:00Z", temperature, "99.000000", null, null);

        createJob("2026-08-19T01:00:00Z", "2026-08-19T01:05:00Z", "PT5M", sensorA.getExternalId(), null);
        assertThat(worker.processOneAvailableWindow()).isTrue();

        assertBucket(sensorA.getExternalId(), temperature.getId(), "2026-08-19T01:00:00Z", "2026-08-19T01:05:00Z",
                "10.000000", "10.000000", "10.000000", 1);
    }

    @Test
    void aggregatesMultipleSensorsAndParametersIgnoringNullBooleanAndTextValues() throws Exception {
        Medicao first = measurement(sensorA, "2026-08-19T02:01:00Z", temperature, "20.000000", presence, Boolean.TRUE);
        textValue(first, label, "ignored");
        measurement(sensorA, "2026-08-19T02:02:00Z", humidity, "60.000000", null, null);
        measurement(sensorB, "2026-08-19T02:03:00Z", temperature, "30.000000", null, null);
        measurement(sensorB, "2026-08-19T02:04:00Z", temperature, null, null, null);

        createJob("2026-08-19T02:00:00Z", "2026-08-19T02:05:00Z", "PT5M", null, null);
        assertThat(worker.processOneAvailableWindow()).isTrue();

        assertBucket(sensorA.getExternalId(), temperature.getId(), "2026-08-19T02:00:00Z", "2026-08-19T02:05:00Z",
                "20.000000", "20.000000", "20.000000", 1);
        assertBucket(sensorA.getExternalId(), humidity.getId(), "2026-08-19T02:00:00Z", "2026-08-19T02:05:00Z",
                "60.000000", "60.000000", "60.000000", 1);
        assertBucket(sensorB.getExternalId(), temperature.getId(), "2026-08-19T02:00:00Z", "2026-08-19T02:05:00Z",
                "30.000000", "30.000000", "30.000000", 1);
        assertThat(bucketCount()).isEqualTo(3);
    }

    @Test
    void respectsSensorCompartimentoAndCombinedFilters() throws Exception {
        measurement(sensorA, "2026-08-19T03:01:00Z", temperature, "11.000000", null, null);
        measurement(sensorB, "2026-08-19T03:01:00Z", temperature, "22.000000", null, null);

        createJob("2026-08-19T03:00:00Z", "2026-08-19T03:05:00Z", "PT5M", sensorA.getExternalId(), null);
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(bucketCount()).isEqualTo(1);
        assertBucket(sensorA.getExternalId(), temperature.getId(), "2026-08-19T03:00:00Z", "2026-08-19T03:05:00Z",
                "11.000000", "11.000000", "11.000000", 1);

        jdbcTemplate.update("delete from analytics_numeric_bucket");
        createJob("2026-08-19T03:05:00Z", "2026-08-19T03:10:00Z", "PT5M", null, roomB);
        measurement(sensorA, "2026-08-19T03:06:00Z", temperature, "33.000000", null, null);
        measurement(sensorB, "2026-08-19T03:06:00Z", temperature, "44.000000", null, null);
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(bucketCount()).isEqualTo(1);
        assertBucket(sensorB.getExternalId(), temperature.getId(), "2026-08-19T03:05:00Z", "2026-08-19T03:10:00Z",
                "44.000000", "44.000000", "44.000000", 1);

        jdbcTemplate.update("delete from analytics_numeric_bucket");
        createJob("2026-08-19T03:10:00Z", "2026-08-19T03:15:00Z", "PT5M", sensorA.getExternalId(), roomB);
        measurement(sensorA, "2026-08-19T03:11:00Z", temperature, "55.000000", null, null);
        measurement(sensorB, "2026-08-19T03:11:00Z", temperature, "66.000000", null, null);
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(bucketCount()).isZero();
    }

    @Test
    void emptyAndPartialWindowsCompleteWithoutArtificialBuckets() throws Exception {
        JsonNode job = createJob("2026-08-19T04:00:00Z", "2026-08-19T04:12:00Z", "PT5M", sensorA.getExternalId(), null);
        assertThat(job.get("windows").get(2).get("from").asText()).isEqualTo("2026-08-19T04:10:00Z");
        assertThat(job.get("windows").get(2).get("to").asText()).isEqualTo("2026-08-19T04:12:00Z");

        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(worker.processOneAvailableWindow()).isFalse();
        assertThat(bucketCount()).isZero();
        mvc.perform(get("/api/analytics/aggregation-jobs/" + job.get("id").asText())
                        .with(user("analyst").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void reexecutionIsIdempotentAcrossJobsAndUpdatesAfterRecomputation() throws Exception {
        measurement(sensorA, "2026-08-19T05:01:00Z", temperature, "10.000000", null, null);
        JsonNode first = createJob("2026-08-19T05:00:00Z", "2026-08-19T05:05:00Z", "PT5M", sensorA.getExternalId(), null);
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertBucket(sensorA.getExternalId(), temperature.getId(), "2026-08-19T05:00:00Z", "2026-08-19T05:05:00Z",
                "10.000000", "10.000000", "10.000000", 1);

        JsonNode second = createJob("2026-08-19T05:00:00Z", "2026-08-19T05:05:00Z", "PT1M", sensorA.getExternalId(), null);
        UUID sameBucketWindowId = UUID.fromString(second.get("windows").get(0).get("id").asText());
        processor.process(new AggregationWindowWork(
                sameBucketWindowId,
                UUID.fromString(second.get("id").asText()),
                0,
                Instant.parse("2026-08-19T05:00:00Z"),
                Instant.parse("2026-08-19T05:05:00Z"),
                sensorA.getExternalId(),
                null,
                1
        ));
        assertThat(bucketCount()).isEqualTo(1);

        measurement(sensorA, "2026-08-19T05:02:00Z", temperature, "30.000000", null, null);
        processor.process(new AggregationWindowWork(
                UUID.fromString(first.get("windows").get(0).get("id").asText()),
                UUID.fromString(first.get("id").asText()),
                0,
                Instant.parse("2026-08-19T05:00:00Z"),
                Instant.parse("2026-08-19T05:05:00Z"),
                sensorA.getExternalId(),
                null,
                1
        ));
        assertBucket(sensorA.getExternalId(), temperature.getId(), "2026-08-19T05:00:00Z", "2026-08-19T05:05:00Z",
                "20.000000", "10.000000", "30.000000", 2);

        jdbcTemplate.update("delete from parametro_valor where parametro_def_id = ?", temperature.getId());
        processor.process(new AggregationWindowWork(
                UUID.fromString(first.get("windows").get(0).get("id").asText()),
                UUID.fromString(first.get("id").asText()),
                0,
                Instant.parse("2026-08-19T05:00:00Z"),
                Instant.parse("2026-08-19T05:05:00Z"),
                sensorA.getExternalId(),
                null,
                1
        ));
        assertThat(bucketCount()).isZero();
    }

    @Test
    void concurrentWorkersDoNotDuplicateBuckets() throws Exception {
        measurement(sensorA, "2026-08-19T06:01:00Z", temperature, "10.000000", null, null);
        createJob("2026-08-19T06:00:00Z", "2026-08-19T06:05:00Z", "PT5M", sensorA.getExternalId(), null);
        CountDownLatch ready = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> {
                ready.countDown();
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                return worker.processOneAvailableWindow();
            });
            var right = executor.submit(() -> {
                ready.countDown();
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                return worker.processOneAvailableWindow();
            });
            assertThat(left.get(10, TimeUnit.SECONDS) || right.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(left.get(10, TimeUnit.SECONDS) && right.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            executor.shutdownNow();
        }
        assertThat(bucketCount()).isEqualTo(1);
    }

    @Test
    void rollbackKeepsWindowUncompletedWhenBucketPersistenceFails() throws Exception {
        measurement(sensorA, "2026-08-19T07:01:00Z", temperature, "10.000000", null, null);
        JsonNode job = createJob("2026-08-19T07:00:00Z", "2026-08-19T07:05:00Z", "PT5M", sensorA.getExternalId(), null);
        UUID windowId = UUID.fromString(job.get("windows").get(0).get("id").asText());
        jdbcTemplate.update("alter table analytics_numeric_bucket add constraint ck_test_average_impossible check (average_value < 0)");
        try {
            assertThat(worker.processOneAvailableWindow()).isTrue();
            assertThat(bucketCount()).isZero();
            assertThat(windowStatus(windowId)).isEqualTo("PENDING");
            assertThat(windowAttempts(windowId)).isOne();
            mvc.perform(get("/api/analytics/aggregation-jobs/" + job.get("id").asText())
                            .with(user("analyst").roles("ANALISTA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PROCESSING"));
        } finally {
            jdbcTemplate.update("alter table analytics_numeric_bucket drop constraint ck_test_average_impossible");
        }
    }

    @Test
    void migrationV19HasConstraintsAndIndexes() {
        Integer constraints = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_name = 'analytics_numeric_bucket'
                  and constraint_name in (
                      'ux_analytics_numeric_bucket_identity',
                      'fk_analytics_numeric_bucket_sensor',
                      'fk_analytics_numeric_bucket_parametro',
                      'fk_analytics_numeric_bucket_compartimento',
                      'ck_analytics_numeric_bucket_period',
                      'ck_analytics_numeric_bucket_count'
                  )
                """, Integer.class);
        assertThat(constraints).isEqualTo(6);

        Integer indexes = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and indexname in (
                      'ix_analytics_numeric_bucket_sensor_start',
                      'ix_analytics_numeric_bucket_param_start',
                      'ix_analytics_numeric_bucket_compartimento_start',
                      'ix_parametro_valor_numeric_aggregation'
                  )
                """, Integer.class);
        assertThat(indexes).isEqualTo(4);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into analytics_numeric_bucket
                (sensor_external_id, parametro_def_id, compartimento_id, bucket_start, bucket_end,
                 aggregation_version, average_value, minimum_value, maximum_value, sample_count, created_at, updated_at)
                values (?, ?, ?, '2026-08-19T00:05:00Z', '2026-08-19T00:00:00Z',
                        1, 1, 1, 1, 1, now(), now())
                """, sensorA.getExternalId(), temperature.getId(), roomA)).isInstanceOf(RuntimeException.class);
    }

    private JsonNode createJob(String from, String to, String duration, String sensorExternalId, String roomId) throws Exception {
        String sensorJson = sensorExternalId == null ? "" : ",\"sensorExternalId\":\"" + sensorExternalId + "\"";
        String roomJson = roomId == null ? "" : ",\"compartimentoId\":\"" + roomId + "\"";
        String response = mvc.perform(post("/api/analytics/aggregation-jobs")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"from":"%s","to":"%s","windowDuration":"%s"%s%s}
                                """.formatted(from, to, duration, sensorJson, roomJson)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private Medicao measurement(
            Sensor sensor,
            String timestamp,
            ParametroDef numericParam,
            String numericValue,
            ParametroDef booleanParam,
            Boolean booleanValue
    ) {
        Medicao medicao = medicaoRepository.save(new Medicao(sensor, Instant.parse(timestamp), Instant.parse(timestamp), "TEST"));
        if (numericParam != null) {
            ParametroValor value = new ParametroValor(medicao, numericParam);
            if (numericValue != null) {
                value.setNumericValue(new BigDecimal(numericValue));
            }
            parametroValorRepository.save(value);
        }
        if (booleanParam != null) {
            ParametroValor value = new ParametroValor(medicao, booleanParam);
            value.setBooleanValue(booleanValue);
            parametroValorRepository.save(value);
        }
        return medicao;
    }

    private void textValue(Medicao medicao, ParametroDef parametroDef, String value) {
        ParametroValor parametroValor = new ParametroValor(medicao, parametroDef);
        parametroValor.setTextValue(value);
        parametroValorRepository.save(parametroValor);
    }

    private void assertBucket(
            String sensorExternalId,
            UUID parametroDefId,
            String from,
            String to,
            String average,
            String minimum,
            String maximum,
            long count
    ) {
        jdbcTemplate.queryForObject("""
                select average_value, minimum_value, maximum_value, sample_count
                from analytics_numeric_bucket
                where sensor_external_id = ?
                  and parametro_def_id = ?
                  and bucket_start = ?::timestamptz
                  and bucket_end = ?::timestamptz
                  and aggregation_version = 1
                """, (rs, rowNum) -> {
            assertThat(rs.getBigDecimal("average_value")).isEqualByComparingTo(average);
            assertThat(rs.getBigDecimal("minimum_value")).isEqualByComparingTo(minimum);
            assertThat(rs.getBigDecimal("maximum_value")).isEqualByComparingTo(maximum);
            assertThat(rs.getLong("sample_count")).isEqualTo(count);
            return true;
        }, sensorExternalId, parametroDefId, from, to);
    }

    private int bucketCount() {
        return jdbcTemplate.queryForObject("select count(*) from analytics_numeric_bucket", Integer.class);
    }

    private String windowStatus(UUID windowId) {
        return jdbcTemplate.queryForObject("select status from analytics_aggregation_window where id = ?", String.class, windowId);
    }

    private String windowError(UUID windowId) {
        return jdbcTemplate.queryForObject("select error from analytics_aggregation_window where id = ?", String.class, windowId);
    }

    private int windowAttempts(UUID windowId) {
        return jdbcTemplate.queryForObject("select attempts from analytics_aggregation_window where id = ?", Integer.class, windowId);
    }
}
