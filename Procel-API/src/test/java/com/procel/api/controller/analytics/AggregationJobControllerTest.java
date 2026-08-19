package com.procel.api.controller.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.dto.analytics.AggregationJobDTOs;
import com.procel.api.entity.rooms.Campus;
import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.rooms.Predio;
import com.procel.api.entity.rooms.Unidade;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.Sensor;
import com.procel.api.entity.sensors.TipoDeSensor;
import com.procel.api.repository.rooms.CampusRepository;
import com.procel.api.repository.rooms.CompartimentoRepository;
import com.procel.api.repository.rooms.PredioRepository;
import com.procel.api.repository.rooms.UnidadeRepository;
import com.procel.api.repository.sensors.MedicaoRepository;
import com.procel.api.repository.sensors.SensorRepository;
import com.procel.api.repository.sensors.TipoDeSensorRepository;
import com.procel.api.service.analytics.AggregationJobService;
import com.procel.api.service.analytics.AggregationJobWorker;
import com.procel.api.service.analytics.AggregationWindowProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AggregationJobControllerTest {
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
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AggregationJobService jobService;
    @Autowired AggregationJobWorker worker;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CampusRepository campusRepository;
    @Autowired UnidadeRepository unidadeRepository;
    @Autowired PredioRepository predioRepository;
    @Autowired CompartimentoRepository compartimentoRepository;
    @Autowired TipoDeSensorRepository tipoDeSensorRepository;
    @Autowired SensorRepository sensorRepository;
    @Autowired MedicaoRepository medicaoRepository;
    @MockitoBean AggregationWindowProcessor processor;

    private String sensorId;
    private String compartimentoId;

    @BeforeEach
    void setUp() {
        Mockito.reset(processor);
        doNothing().when(processor).process(any());
        jdbcTemplate.update("delete from analytics_aggregation_window");
        jdbcTemplate.update("delete from analytics_aggregation_job");
        String suffix = UUID.randomUUID().toString();
        Campus campus = campusRepository.save(new Campus("Campus analytics " + suffix));
        Unidade unidade = unidadeRepository.save(new Unidade("Unidade analytics " + suffix));
        Predio predio = predioRepository.save(new Predio(campus, "Predio analytics " + suffix));
        compartimentoId = "ROOM-ANALYTICS-" + suffix;
        Compartimento compartimento = compartimentoRepository.save(
                new Compartimento(compartimentoId, predio, unidade, "Sala analytics " + suffix, "Sala"));
        TipoDeSensor tipo = tipoDeSensorRepository.save(new TipoDeSensor("TYPE-ANALYTICS-" + suffix));
        sensorId = "SENSOR-ANALYTICS-" + suffix;
        Sensor sensor = sensorRepository.save(new Sensor(sensorId, "Sensor analytics " + suffix, tipo, compartimento));
        medicaoRepository.save(new Medicao(sensor, Instant.parse("2026-08-19T00:01:00Z"), Instant.parse("2026-08-19T00:01:01Z"), "TEST"));
    }

    @Test
    void rejectsInvalidPeriodAndWindow() throws Exception {
        mvc.perform(post("/api/analytics/aggregation-jobs")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"from":"2026-08-19T01:00:00Z","to":"2026-08-19T01:00:00Z","windowDuration":"PT5M"}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/analytics/aggregation-jobs")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"from":"2026-08-19T00:00:00Z","to":"2026-08-19T01:00:00Z","windowDuration":"PT0S"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsExactAndPartialWindowsWithoutBuckets() throws Exception {
        String exactJobId = createJob("2026-08-19T00:00:00Z", "2026-08-19T00:15:00Z", "PT5M", sensorId, null)
                .get("id").asText();
        mvc.perform(get("/api/analytics/aggregation-jobs/" + exactJobId)
                        .with(user("analyst").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.progress.totalWindows").value(3))
                .andExpect(jsonPath("$.windows[0].from").value("2026-08-19T00:00:00Z"))
                .andExpect(jsonPath("$.windows[0].to").value("2026-08-19T00:05:00Z"))
                .andExpect(jsonPath("$.windows[2].to").value("2026-08-19T00:15:00Z"));

        String partialJobId = createJob("2026-08-19T00:00:00Z", "2026-08-19T00:12:00Z", "PT5M", sensorId, null)
                .get("id").asText();
        mvc.perform(get("/api/analytics/aggregation-jobs/" + partialJobId)
                        .with(user("operator").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.totalWindows").value(3))
                .andExpect(jsonPath("$.windows[2].from").value("2026-08-19T00:10:00Z"))
                .andExpect(jsonPath("$.windows[2].to").value("2026-08-19T00:12:00Z"));

        Integer bucketTables = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name like 'analytics_%bucket%'
                """, Integer.class);
        assertThat(bucketTables).isZero();
    }

    @Test
    void returnsExistingJobForSequentialAndConcurrentEquivalentRequests() throws Exception {
        JsonNode first = createJob("2026-08-19T02:00:00Z", "2026-08-19T03:00:00Z", "PT10M", sensorId, compartimentoId);
        JsonNode second = createJob("2026-08-19T02:00:00Z", "2026-08-19T03:00:00Z", "PT10M", sensorId, compartimentoId);
        assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());

        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<AggregationJobDTOs.AggregationJobResponse>> tasks = List.of(
                    () -> jobService.create(request("2026-08-19T04:00:00Z", "2026-08-19T05:00:00Z", "PT10M"), "admin"),
                    () -> jobService.create(request("2026-08-19T04:00:00Z", "2026-08-19T05:00:00Z", "PT10M"), "admin")
            );
            var futures = executor.invokeAll(tasks);
            String left = futures.get(0).get(10, TimeUnit.SECONDS).id().toString();
            String right = futures.get(1).get(10, TimeUnit.SECONDS).id().toString();
            assertThat(right).isEqualTo(left);
            Integer jobs = jdbcTemplate.queryForObject("""
                    select count(*)
                    from analytics_aggregation_job
                    where requested_from = '2026-08-19T04:00:00Z'
                    """, Integer.class);
            assertThat(jobs).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentClaimAllowsOnlyOneWorkerForSameWindow() throws Exception {
        JsonNode job = createJob("2026-08-19T06:00:00Z", "2026-08-19T06:05:00Z", "PT5M", sensorId, null);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(processor).process(any());

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> worker.processOneAvailableWindow());
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> worker.processOneAvailableWindow());
            assertThat(second.get(5, TimeUnit.SECONDS)).isFalse();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        mvc.perform(get("/api/analytics/aggregation-jobs/" + job.get("id").asText())
                        .with(user("analyst").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress.completedWindows").value(1));
    }

    @Test
    void recoversExpiredLeaseAndRetriesFailuresWithBackoffLimit() throws Exception {
        JsonNode leaseJob = createJob("2026-08-19T07:00:00Z", "2026-08-19T07:05:00Z", "PT5M", sensorId, null);
        UUID leaseWindowId = UUID.fromString(leaseJob.get("windows").get(0).get("id").asText());
        jdbcTemplate.update("""
                update analytics_aggregation_window
                set status = 'PROCESSING', attempts = 1, locked_at = now() - interval '10 seconds', locked_by = 'dead-worker'
                where id = ?
                """, leaseWindowId);
        assertThat(worker.processOneAvailableWindow()).isTrue();
        verify(processor, timeout(1000)).process(any());

        Mockito.reset(processor);
        doThrow(new IllegalStateException("temporary failure")).when(processor).process(any());
        JsonNode retryJob = createJob("2026-08-19T08:00:00Z", "2026-08-19T08:05:00Z", "PT5M", sensorId, null);
        UUID retryWindowId = UUID.fromString(retryJob.get("windows").get(0).get("id").asText());
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(windowStatus(retryWindowId)).isEqualTo("PENDING");
        Integer attempts = jdbcTemplate.queryForObject(
                "select attempts from analytics_aggregation_window where id = ?",
                Integer.class,
                retryWindowId
        );
        assertThat(attempts).isOne();

        jdbcTemplate.update("update analytics_aggregation_window set next_attempt_at = now() where id = ?", retryWindowId);
        assertThat(worker.processOneAvailableWindow()).isTrue();
        assertThat(windowStatus(retryWindowId)).isEqualTo("FAILED");
        mvc.perform(get("/api/analytics/aggregation-jobs/" + retryJob.get("id").asText())
                        .with(user("analyst").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.progress.failedWindows").value(1));
    }

    @Test
    void disabledBatchDoesNotProcessWindows() throws Exception {
        createJob("2026-08-19T09:00:00Z", "2026-08-19T09:05:00Z", "PT5M", sensorId, null);
        assertThat(worker.processAvailableBatch()).isZero();
        verifyNoInteractions(processor);
    }

    @Test
    void enforcesAuthenticationAndAuthorization() throws Exception {
        String body = """
                {"from":"2026-08-19T10:00:00Z","to":"2026-08-19T10:05:00Z","windowDuration":"PT5M"}
                """;
        mvc.perform(post("/api/analytics/aggregation-jobs")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/analytics/aggregation-jobs")
                        .with(user("analyst").roles("ANALISTA"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/analytics/aggregation-jobs")
                        .with(user("operator").roles("OPERADOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    void databaseConstraintsRejectInvalidWindows() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into analytics_aggregation_job
                (id, idempotency_key, requested_from, requested_to, window_duration_seconds,
                 requested_by, created_at, status, total_windows)
                values (?, repeat('a', 64), '2026-08-19T00:00:00Z', '2026-08-19T01:00:00Z',
                        300, 'admin', now(), 'PENDING', 1)
                """, jobId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into analytics_aggregation_window
                (job_id, window_index, window_from, window_to, status, next_attempt_at)
                values (?, 0, '2026-08-19T00:05:00Z', '2026-08-19T00:05:00Z', 'PENDING', now())
                """, jobId)).isInstanceOf(RuntimeException.class);
    }

    private JsonNode createJob(
            String from,
            String to,
            String duration,
            String sensorExternalId,
            String roomId
    ) throws Exception {
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

    private AggregationJobDTOs.CreateAggregationJobRequest request(String from, String to, String duration) {
        return new AggregationJobDTOs.CreateAggregationJobRequest(
                Instant.parse(from),
                Instant.parse(to),
                java.time.Duration.parse(duration),
                sensorId,
                compartimentoId
        );
    }

    private String windowStatus(UUID windowId) {
        return jdbcTemplate.queryForObject(
                "select status from analytics_aggregation_window where id = ?",
                String.class,
                windowId
        );
    }
}
