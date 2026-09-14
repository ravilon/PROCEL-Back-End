package com.procel.api.service.missions.evaluation;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.dto.sensors.SensorTelemetryIngestDTOs;
import com.procel.api.entity.missions.EventoPoliticaAtribuicao;
import com.procel.api.entity.missions.MissaoCicloTipo;
import com.procel.api.entity.sensors.MedicaoIngestaoSource;
import com.procel.api.service.missions.EventoAvaliacaoRequestService;
import com.procel.api.service.sensors.ParametroQualificacaoService;
import com.procel.api.service.sensors.SensorIngestOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MissionEventEvaluationIntegrationTest {

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

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired SensorIngestOrchestrator orchestrator;
    @Autowired EventoAvaliacaoRequestService requestService;
    @Autowired MissionEventEvaluationWorker worker;
    @Autowired MissionTemporalWindowWorker temporalWorker;
    @Autowired MissionTemporalActivityProcessor temporalActivityProcessor;
    @Autowired MissionEvaluationProperties properties;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean ParametroQualificacaoService qualificacaoService;

    String suffix;
    String sensorId;
    String roomId;
    String tipoSensor;
    UUID parametroDefId;
    UUID eventId;
    UUID missionId;
    Long disciplinaId;
    Instant measuredAt;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        suffix = UUID.randomUUID().toString();
        sensorId = "SII-" + suffix;
        roomId = "ROOM-" + suffix;
        tipoSensor = "TYPE-" + suffix;
        parametroDefId = UUID.randomUUID();
        disciplinaId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
        measuredAt = Instant.parse("2026-09-13T10:00:00Z");
        properties.setWorkerEnabled(false);
        properties.setBatchSize(20);
        properties.setLeaseDuration(Duration.ofSeconds(30));
        properties.setInitialBackoff(Duration.ofSeconds(1));
        properties.setMaxBackoff(Duration.ofSeconds(5));
        properties.setMaxAttempts(3);
        properties.getTemporalWindows().setEnabled(false);
        properties.getTemporalWindows().setWorkerEnabled(false);
        properties.getTemporalWindows().setDroolsEnabled(false);
        properties.getTemporalWindows().setActivitiesEnabled(false);
        properties.getTemporalWindows().setMaximumSampleGap(Duration.ofSeconds(900));
        properties.getTemporalWindows().setMaxAttempts(3);
        properties.getTemporalWindows().setBatchSize(20);
        properties.getTemporalWindows().setLeaseDuration(Duration.ofSeconds(30));
        properties.getTemporalWindows().setInitialBackoff(Duration.ofSeconds(1));
        properties.getTemporalWindows().setMaxBackoff(Duration.ofSeconds(5));
        seedSensor();
    }

    @Test
    void canonicalIngestionCreatesEvaluationRequestAndDuplicateDoesNotCreateAnother() {
        var first = ingest("msg-ingest", BigDecimal.valueOf(25));
        var second = orchestrator.ingest("producer", request("msg-ingest", BigDecimal.valueOf(25)));

        assertThat(first.status().value()).isEqualTo(201);
        assertThat(second.status().value()).isEqualTo(200);
        assertThat(count("evento_avaliacao_request")).isEqualTo(1);
        assertThat(requestCountFor(first.response().medicaoId())).isEqualTo(1);
    }

    @Test
    void profileAndTelemetryCanonicalFlowsCreateEvaluationRequests() {
        var profile = seedActiveProfile();

        var profileOutcome = orchestrator.ingestWithProfile(
                profile.profileId(),
                profile.parserVersionId(),
                "profile-producer",
                request("msg-profile", BigDecimal.valueOf(25))
        );
        var telemetryOutcome = orchestrator.ingestTelemetryRawWithProfile(
                profile.profileId(),
                profile.parserVersionId(),
                "telemetry-service",
                new SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest(
                        "raw-event-" + suffix,
                        "device-producer",
                        "raw-message-" + suffix,
                        measuredAt,
                        measuredAt,
                        JsonNodeFactory.instance.objectNode()
                ),
                request("msg-telemetry", BigDecimal.valueOf(26))
        );

        assertThat(profileOutcome.status().value()).isEqualTo(201);
        assertThat(telemetryOutcome.status().value()).isEqualTo(201);
        assertThat(count("medicao")).isEqualTo(2);
        assertThat(count("evento_avaliacao_request")).isEqualTo(2);
    }

    @Test
    void rollbackOfMeasurementAlsoRollsBackEvaluationRequest() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                var medicaoId = UUID.randomUUID();
                jdbcTemplate.update("""
                        insert into medicao (id, sensor_external_id, timestamp, recebido_em, source)
                        values (?, ?, ?, ?, 'TEST')
                        """, medicaoId, sensorId, Timestamp.from(measuredAt), Timestamp.from(measuredAt));
                requestService.criarPendente(medicaoId);
                throw new RuntimeException("rollback");
            });
        } catch (RuntimeException ignored) {
            // Expected rollback.
        }

        assertThat(count("medicao")).isEqualTo(0);
        assertThat(count("evento_avaliacao_request")).isEqualTo(0);
    }

    @Test
    void claimIsExclusiveRecoversExpiredLeaseAndSkipsFutureRequests() throws Exception {
        UUID medicao1 = ingest("msg-claim-1", BigDecimal.valueOf(25)).response().medicaoId();
        UUID medicao2 = ingest("msg-claim-2", BigDecimal.valueOf(25)).response().medicaoId();
        jdbcTemplate.update("update evento_avaliacao_request set available_at = now() - interval '1 second' where medicao_id = ?", medicao1);
        jdbcTemplate.update("update evento_avaliacao_request set available_at = now() + interval '10 minutes' where medicao_id = ?", medicao2);

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var f1 = executor.submit(() -> {
            start.await();
            return requestService.claimAvailable(20, Duration.ofSeconds(30), 3);
        });
        var f2 = executor.submit(() -> {
            start.await();
            return requestService.claimAvailable(20, Duration.ofSeconds(30), 3);
        });
        start.countDown();

        int claimed = f1.get().size() + f2.get().size();
        executor.shutdownNow();

        assertThat(claimed).isEqualTo(1);
        assertThat(statusFor(medicao1)).isEqualTo("PROCESSING");
        assertThat(statusFor(medicao2)).isEqualTo("PENDING");

        jdbcTemplate.update("update evento_avaliacao_request set lease_until = now() - interval '1 second' where medicao_id = ?", medicao1);
        assertThat(claimOne().medicaoId()).isEqualTo(medicao1);
    }

    @Test
    void workerDisabledByDefaultCanBeKeptIdle() {
        ingest("msg-disabled", BigDecimal.valueOf(25));
        properties.setWorkerEnabled(false);

        assertThat(worker.processAvailableBatch()).isZero();
        assertThat(count("evento_ocorrencia")).isEqualTo(0);
    }

    @Test
    void temporalDisabledByDefaultDoesNotOpenWindow() {
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);
        UUID medicaoId = ingest("msg-temporal-disabled", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(medicaoId);

        assertThat(statusFor(medicaoId)).isEqualTo("IGNORED");
        assertThat(count("evento_janela_avaliacao")).isZero();
    }

    @Test
    void transitionDisabledByDefaultDoesNotCreateOccurrence() {
        seedTransitionEvent("temperature", parametroDefId, "NUMERIC", "GT", BigDecimal.valueOf(20), null, null);
        UUID first = ingestAt("msg-transition-disabled-first", measuredAt, BigDecimal.valueOf(18)).response().medicaoId();
        UUID second = ingestAt("msg-transition-disabled-second", measuredAt.plusSeconds(60), BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(first);
        processClaimed(second);

        assertThat(statusFor(second)).isEqualTo("IGNORED");
        assertThat(count("evento_ocorrencia")).isZero();
    }

    @Test
    void firstTransitionMeasurementIsNotATransitionAndNumericChangeCreatesOccurrenceEvidence() {
        enableTemporal(false);
        seedTransitionEvent("temperature", parametroDefId, "NUMERIC", "GT", BigDecimal.valueOf(20), null, null);
        UUID first = ingestAt("msg-transition-first", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(first);

        assertThat(statusFor(first)).isEqualTo("COMPLETED");
        assertThat(count("evento_ocorrencia")).isZero();

        UUID second = ingestAt("msg-transition-second", measuredAt.plusSeconds(60), BigDecimal.valueOf(26)).response().medicaoId();
        processClaimed(second);

        assertThat(statusFor(second)).as(lastErrorFor(second)).isEqualTo("COMPLETED");
        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("CONFIRMADO");
        assertThat(jdbcTemplate.queryForObject("select contexto_snapshot::text from evento_ocorrencia", String.class))
                .contains("TRANSITION_V1")
                .contains(second.toString())
                .contains("previousObservedValue")
                .contains("currentObservedValue");
        assertThat(countWhere("evento_ocorrencia_evidencia", "papel = 'ANTES'")).isEqualTo(1);
        assertThat(countWhere("evento_ocorrencia_evidencia", "papel = 'DEPOIS'")).isEqualTo(1);
        assertThat(count("atividade")).isZero();
        assertThat(count("xp_lancamento")).isZero();
    }

    @Test
    void transitionSupportsBooleanAndTextValues() {
        enableTemporal(false);
        UUID booleanParam = seedParametroDef("presence", "BOOLEAN");
        UUID textParam = seedParametroDef("mode", "TEXT");
        seedTransitionEvent("presence", booleanParam, "BOOLEAN", "EQ", null, true, null);
        UUID booleanFirst = ingestRawAt("msg-transition-bool-first", measuredAt, Map.of("temperature", BigDecimal.valueOf(20), "presence", false)).response().medicaoId();
        UUID booleanSecond = ingestRawAt("msg-transition-bool-second", measuredAt.plusSeconds(60), Map.of("temperature", BigDecimal.valueOf(20), "presence", true)).response().medicaoId();

        processClaimed(booleanFirst);
        processClaimed(booleanSecond);

        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(countWhere("evento_ocorrencia_evidencia", "papel in ('ANTES','DEPOIS')")).isEqualTo(2);

        cleanDatabase();
        setUp();
        enableTemporal(false);
        textParam = seedParametroDef("mode", "TEXT");
        seedTransitionEvent("mode", textParam, "TEXT", "EQ", null, null, "eco");
        UUID textFirst = ingestRawAt("msg-transition-text-first", measuredAt, Map.of("temperature", BigDecimal.valueOf(20), "mode", "off")).response().medicaoId();
        UUID textSecond = ingestRawAt("msg-transition-text-second", measuredAt.plusSeconds(60), Map.of("temperature", BigDecimal.valueOf(20), "mode", "eco")).response().medicaoId();

        processClaimed(textFirst);
        processClaimed(textSecond);

        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(countWhere("evento_ocorrencia_evidencia", "papel in ('ANTES','DEPOIS')")).isEqualTo(2);
    }

    @Test
    void transitionRetryDoesNotDuplicateOccurrenceEvidenceActivitiesOrXp() {
        enableTemporal(false);
        properties.getTemporalWindows().setActivitiesEnabled(true);
        seedAcademicContext("student-transition");
        seedTransitionEvent("temperature", parametroDefId, "NUMERIC", "GT", BigDecimal.valueOf(20), null, null,
                EventoPoliticaAtribuicao.ALUNOS_VINCULADOS, MissaoCicloTipo.UNICA, 1, true, 10);
        UUID first = ingestAt("msg-transition-effects-first", measuredAt, BigDecimal.valueOf(19)).response().medicaoId();
        UUID second = ingestAt("msg-transition-effects-second", measuredAt.plusSeconds(60), BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(first);
        var work = claimOne(second);
        worker.processClaimed(work);
        jdbcTemplate.update("""
                update evento_avaliacao_request
                set status = 'RETRY', available_at = now(), processed_at = null
                where id = ?
                """, work.requestId());
        worker.processClaimed(claimOne(second));

        assertThat(statusFor(second)).as(lastErrorFor(second)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("PROCESSADO");
        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(countWhere("evento_ocorrencia_evidencia", "papel = 'ANTES'")).isEqualTo(1);
        assertThat(countWhere("evento_ocorrencia_evidencia", "papel = 'DEPOIS'")).isEqualTo(1);
        assertThat(count("atividade")).isEqualTo(1);
        assertThat(countWhere("atividade_evento", "tipo = 'PROGRESSO'")).isEqualTo(1);
        assertThat(countWhere("atividade_evento", "tipo = 'CONCLUSAO'")).isEqualTo(1);
        assertThat(count("xp_lancamento")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select coalesce(sum(quantidade), 0) from xp_lancamento", Long.class)).isEqualTo(10L);
    }

    @Test
    void transitionUsesLatestStrictlyPreviousMeasurementByTimestamp() {
        enableTemporal(false);
        seedTransitionEvent("temperature", parametroDefId, "NUMERIC", "GT", BigDecimal.valueOf(20), null, null);
        UUID older = ingestAt("msg-transition-order-older", measuredAt, BigDecimal.valueOf(10)).response().medicaoId();
        UUID previous = ingestAt("msg-transition-order-prev", measuredAt.plusSeconds(60), BigDecimal.valueOf(25)).response().medicaoId();
        UUID sameTimestamp = ingestAt("msg-transition-order-same", measuredAt.plusSeconds(120), BigDecimal.valueOf(5)).response().medicaoId();
        UUID current = ingestAt("msg-transition-order-current", measuredAt.plusSeconds(120), BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(older);
        processClaimed(previous);
        processClaimed(sameTimestamp);
        processClaimed(current);

        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from evento_ocorrencia_evidencia evidence
                join parametro_valor value on value.id = evidence.parametro_valor_id
                where evidence.papel = 'ANTES'
                  and value.medicao_id = ?
                """, Long.class, sameTimestamp)).isZero();
    }

    @Test
    void primeiraMedicaoAbreJanelaTemporal() {
        enableTemporal(false);
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);

        UUID medicaoId = ingest("msg-temporal-open", BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(medicaoId);

        assertThat(count("evento_janela_avaliacao")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from evento_janela_avaliacao", String.class)).isEqualTo("ABERTA");
        assertThat(count("evento_janela_evidencia")).isEqualTo(1);
    }

    @Test
    void sequenciaValidaMantemJanelaEFatosForaDeOrdemNaoImpedemConclusao() {
        enableTemporal(true);
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);

        UUID start = ingestAt("msg-temporal-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(start);
        UUID mid = ingestAt("msg-temporal-mid", measuredAt.plusSeconds(600), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(mid);
        UUID mid2 = ingestAt("msg-temporal-mid-2", measuredAt.plusSeconds(1200), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(mid2);
        UUID end = ingestAt("msg-temporal-end", measuredAt.plusSeconds(1800), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(end);

        properties.getTemporalWindows().setWorkerEnabled(true);
        assertThat(temporalWorker.processAvailableBatch()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select status from evento_janela_avaliacao", String.class)).isEqualTo("SATISFEITA");
        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("CONFIRMADO");
        assertThat(count("evento_ocorrencia_evidencia")).isGreaterThanOrEqualTo(1);
        assertThat(count("atividade")).isZero();
        assertThat(count("xp_lancamento")).isZero();
    }

    @Test
    void temporalActivitiesEnabledCreatesStudentActivitiesProgressCompletionAndXp() {
        enableTemporal(true);
        properties.getTemporalWindows().setActivitiesEnabled(true);
        seedAcademicContext("student-temporal-a", "student-temporal-b");
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800,
                EventoPoliticaAtribuicao.ALUNOS_VINCULADOS, MissaoCicloTipo.POR_AULA, 1, true, 10);

        satisfyTemporalWindow("temporal-activities");

        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("PROCESSADO");
        assertThat(count("atividade")).isEqualTo(2);
        assertThat(countWhere("atividade", "status = 'CONCLUIDA' and progresso_atual = 1 and progresso_necessario = 1")).isEqualTo(2);
        assertThat(countWhere("atividade_evento", "tipo = 'PROGRESSO' and progresso_adicionado = 1")).isEqualTo(2);
        assertThat(countWhere("atividade_evento", "tipo = 'CONCLUSAO' and progresso_adicionado = 0")).isEqualTo(2);
        assertThat(count("xp_lancamento")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select coalesce(sum(quantidade), 0) from xp_lancamento", Long.class)).isEqualTo(20L);
    }

    @Test
    void janelaEncerradaAbreAvaliaNoFimEGeraOcorrenciaIdempotente() {
        enableTemporal(false);
        seedWindowClosedEvent(BigDecimal.valueOf(20), EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA,
                MissaoCicloTipo.UNICA, 1, true, 10);

        UUID start = ingestAt("msg-window-closed-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        UUID mid = ingestAt("msg-window-closed-mid", measuredAt.plusSeconds(60), BigDecimal.valueOf(30)).response().medicaoId();
        UUID endBoundary = ingestAt("msg-window-closed-boundary", measuredAt.plusSeconds(120), BigDecimal.valueOf(5)).response().medicaoId();
        processClaimed(start);
        processClaimed(mid);
        processClaimed(endBoundary);

        properties.getTemporalWindows().setWorkerEnabled(true);
        assertThat(temporalWorker.processAvailableBatch()).isEqualTo(1);
        jdbcTemplate.update("""
                update evento_janela_avaliacao
                set status = 'ABERTA', lease_until = null, proxima_avaliacao_em = now()
                """);
        assertThat(temporalWorker.processAvailableBatch()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select status from evento_janela_avaliacao", String.class)).isEqualTo("SATISFEITA");
        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("CONFIRMADO");
        assertThat(jdbcTemplate.queryForObject("select contexto_snapshot::text from evento_ocorrencia", String.class))
                .contains("WINDOW_CLOSED_V1")
                .contains("JANELA_ENCERRADA")
                .contains("INSTANTANEO");
        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(countWhere("evento_ocorrencia_evidencia", "papel = 'CONDICAO'")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from evento_ocorrencia_evidencia evidence
                join parametro_valor value on value.id = evidence.parametro_valor_id
                where value.medicao_id = ?
                """, Long.class, endBoundary)).isZero();
    }

    @Test
    void janelaEncerradaInvalidaQuandoUltimoFatoDoIntervaloNaoAtende() {
        enableTemporal(false);
        seedWindowClosedEvent(BigDecimal.valueOf(20), EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA,
                MissaoCicloTipo.UNICA, 1, true, 10);

        UUID start = ingestAt("msg-window-closed-invalid-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        UUID lastInside = ingestAt("msg-window-closed-invalid-last", measuredAt.plusSeconds(60), BigDecimal.valueOf(10)).response().medicaoId();
        UUID boundary = ingestAt("msg-window-closed-invalid-boundary", measuredAt.plusSeconds(120), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(start);
        processClaimed(lastInside);
        processClaimed(boundary);

        properties.getTemporalWindows().setWorkerEnabled(true);
        assertThat(temporalWorker.processAvailableBatch()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select status from evento_janela_avaliacao", String.class)).isEqualTo("INVALIDADA");
        assertThat(count("evento_ocorrencia")).isZero();
    }

    @Test
    void janelaEncerradaComAtividadesFlagCriaProgressoConclusaoEXp() {
        enableTemporal(false);
        properties.getTemporalWindows().setActivitiesEnabled(true);
        seedAcademicContext("student-window-closed-a", "student-window-closed-b");
        seedWindowClosedEvent(BigDecimal.valueOf(20), EventoPoliticaAtribuicao.ALUNOS_VINCULADOS,
                MissaoCicloTipo.POR_AULA, 1, true, 10);

        UUID start = ingestAt("msg-window-closed-effects-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        UUID mid = ingestAt("msg-window-closed-effects-mid", measuredAt.plusSeconds(60), BigDecimal.valueOf(30)).response().medicaoId();
        UUID boundary = ingestAt("msg-window-closed-effects-boundary", measuredAt.plusSeconds(120), BigDecimal.valueOf(30)).response().medicaoId();
        processClaimed(start);
        processClaimed(mid);
        processClaimed(boundary);

        properties.getTemporalWindows().setWorkerEnabled(true);
        assertThat(temporalWorker.processAvailableBatch()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("PROCESSADO");
        assertThat(count("atividade")).isEqualTo(2);
        assertThat(countWhere("atividade", "status = 'CONCLUIDA' and progresso_atual = 1")).isEqualTo(2);
        assertThat(countWhere("atividade_evento", "tipo = 'PROGRESSO'")).isEqualTo(2);
        assertThat(countWhere("atividade_evento", "tipo = 'CONCLUSAO'")).isEqualTo(2);
        assertThat(count("xp_lancamento")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select coalesce(sum(quantidade), 0) from xp_lancamento", Long.class)).isEqualTo(20L);
    }

    @Test
    void enablingTemporalActivitiesLaterProcessesConfirmedPendingOccurrences() {
        enableTemporal(true);
        seedAcademicContext("student-temporal-later");
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800,
                EventoPoliticaAtribuicao.ALUNOS_VINCULADOS, MissaoCicloTipo.POR_AULA, 1, true, 10);

        satisfyTemporalWindow("temporal-later");
        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("CONFIRMADO");
        assertThat(count("atividade")).isZero();

        properties.getTemporalWindows().setActivitiesEnabled(true);
        assertThat(temporalWorker.processAvailableBatch()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("PROCESSADO");
        assertThat(count("atividade")).isEqualTo(1);
        assertThat(count("xp_lancamento")).isEqualTo(1);
    }

    @Test
    void concurrentTemporalActivityProcessingDoesNotDuplicateEffects() throws Exception {
        enableTemporal(true);
        seedAcademicContext("student-temporal-race");
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800,
                EventoPoliticaAtribuicao.ALUNOS_VINCULADOS, MissaoCicloTipo.UNICA, 1, true, 10);
        satisfyTemporalWindow("temporal-race");
        UUID occurrenceId = jdbcTemplate.queryForObject("select id from evento_ocorrencia", UUID.class);
        properties.getTemporalWindows().setActivitiesEnabled(true);

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var f1 = executor.submit(() -> {
            start.await();
            return temporalActivityProcessor.processSatisfiedOccurrence(occurrenceId, measuredAt.plusSeconds(1800)).processed();
        });
        var f2 = executor.submit(() -> {
            start.await();
            return temporalActivityProcessor.processSatisfiedOccurrence(occurrenceId, measuredAt.plusSeconds(1800)).processed();
        });
        start.countDown();
        assertThat(f1.get()).isTrue();
        assertThat(f2.get()).isTrue();
        executor.shutdownNow();

        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("PROCESSADO");
        assertThat(count("atividade")).isEqualTo(1);
        assertThat(countWhere("atividade_evento", "tipo = 'PROGRESSO'")).isEqualTo(1);
        assertThat(countWhere("atividade_evento", "tipo = 'CONCLUSAO'")).isEqualTo(1);
        assertThat(count("xp_lancamento")).isEqualTo(1);
    }

    @Test
    void condicaoFalsaInvalidaEDuracaoInsuficienteNaoSatisfaz() {
        enableTemporal(true);
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);

        UUID invalidStart = ingestAt("msg-temporal-invalid-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(invalidStart);
        UUID invalidFalse = ingestAt("msg-temporal-invalid-false", measuredAt.plusSeconds(600), BigDecimal.valueOf(10)).response().medicaoId();
        processClaimed(invalidFalse);

        assertThat(jdbcTemplate.queryForObject("select status from evento_janela_avaliacao", String.class)).isEqualTo("INVALIDADA");

        cleanDatabase();
        setUp();
        enableTemporal(true);
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);
        UUID shortStart = ingestAt("msg-temporal-short-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(shortStart);
        jdbcTemplate.update("update evento_janela_avaliacao set fim_previsto_em = ?, proxima_avaliacao_em = ?",
                Timestamp.from(measuredAt.plusSeconds(600)), Timestamp.from(measuredAt.plusSeconds(600)));

        properties.getTemporalWindows().setWorkerEnabled(true);
        temporalWorker.processAvailableBatch();

        assertThat(jdbcTemplate.queryForObject("select status from evento_janela_avaliacao", String.class)).isEqualTo("INVALIDADA");
        assertThat(count("evento_ocorrencia")).isZero();
    }

    @Test
    void lacunaExpiraERecuperacaoAposReinicioNaoDuplicaOcorrencia() {
        enableTemporal(true);
        properties.getTemporalWindows().setMaximumSampleGap(Duration.ofSeconds(300));
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);

        UUID gapStart = ingestAt("msg-temporal-gap-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(gapStart);
        UUID gapLate = ingestAt("msg-temporal-gap-late", measuredAt.plusSeconds(600), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(gapLate);

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from evento_janela_avaliacao where status = 'EXPIRADA'
                """, Long.class)).isEqualTo(1L);

        cleanDatabase();
        setUp();
        enableTemporal(true);
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);
        UUID restartStart = ingestAt("msg-temporal-restart-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(restartStart);
        UUID restartMid = ingestAt("msg-temporal-restart-mid", measuredAt.plusSeconds(900), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(restartMid);
        UUID restartEnd = ingestAt("msg-temporal-restart-end", measuredAt.plusSeconds(1800), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(restartEnd);
        properties.getTemporalWindows().setWorkerEnabled(true);
        temporalWorker.processAvailableBatch();
        jdbcTemplate.update("""
                update evento_janela_avaliacao
                set status = 'ABERTA', lease_until = null, proxima_avaliacao_em = now()
                """);
        temporalWorker.processAvailableBatch();

        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(count("evento_ocorrencia_evidencia")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void janelasSaoIsoladasEntreSalasEAulas() {
        enableTemporal(false);
        seedAcademicContext("student-temporal");
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);

        UUID roomA = ingestAt("msg-temporal-room-a", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(roomA);

        String firstRoom = roomId;
        String firstSensor = sensorId;
        setUp();
        enableTemporal(false);
        seedDurationEvent(BigDecimal.valueOf(20), true, 3600, 1800);
        UUID roomB = ingestAt("msg-temporal-room-b", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(roomB);

        assertThat(jdbcTemplate.queryForObject("select count(distinct compartimento_id) from evento_janela_avaliacao", Long.class))
                .isEqualTo(1L);
        assertThat(firstRoom).isNotEqualTo(roomId);
        assertThat(firstSensor).isNotEqualTo(sensorId);
    }

    @Test
    void matchedEventCreatesConfirmedOccurrenceSnapshotEvidenceAndCompletedRequest() {
        seedEvent(true, BigDecimal.valueOf(20), true);
        UUID medicaoId = ingest("msg-match", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(medicaoId);

        assertThat(statusFor(medicaoId)).as(lastErrorFor(medicaoId)).isEqualTo("COMPLETED");
        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("PROCESSADO");
        assertThat(jdbcTemplate.queryForObject("select contexto_snapshot::text from evento_ocorrencia", String.class))
                .contains("SIMPLE_V1")
                .contains(eventId.toString())
                .contains(medicaoId.toString());
        assertThat(count("evento_ocorrencia_evidencia")).isEqualTo(1);
        assertThat(count("atividade")).isZero();
    }

    @Test
    void matchedAcademicEventCreatesActivitiesProgressesAndCompletesStudents() {
        seedAcademicContext("student-a", "student-b");
        seedEvent(true, BigDecimal.valueOf(20), true, EventoPoliticaAtribuicao.ALUNOS_VINCULADOS,
                MissaoCicloTipo.POR_AULA, 1, true);
        UUID medicaoId = ingest("msg-academic", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(medicaoId);

        assertThat(statusFor(medicaoId)).as(lastErrorFor(medicaoId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("select status from evento_ocorrencia", String.class)).isEqualTo("PROCESSADO");
        assertThat(count("atividade")).isEqualTo(2);
        assertThat(countWhere("atividade", "status = 'CONCLUIDA' and progresso_atual = 1 and progresso_necessario = 1")).isEqualTo(2);
        assertThat(countWhere("atividade", "started_at is not null and completed_at is not null")).isEqualTo(2);
        assertThat(countWhere("atividade_evento", "tipo = 'PROGRESSO' and progresso_adicionado = 1")).isEqualTo(2);
        assertThat(countWhere("atividade_evento", "tipo = 'CONCLUSAO' and progresso_adicionado = 0")).isEqualTo(2);
        assertThat(count("xp_lancamento")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select coalesce(sum(quantidade), 0) from xp_lancamento", Long.class)).isEqualTo(20L);
    }

    @Test
    void retryDoesNotDuplicateActivityOrProgress() {
        seedAcademicContext("student-retry");
        seedEvent(true, BigDecimal.valueOf(20), true, EventoPoliticaAtribuicao.ALUNOS_VINCULADOS,
                MissaoCicloTipo.UNICA, 2, true);
        UUID medicaoId = ingest("msg-progress-retry", BigDecimal.valueOf(25)).response().medicaoId();
        var work = claimOne(medicaoId);

        worker.processClaimed(work);
        jdbcTemplate.update("""
                update evento_avaliacao_request
                set status = 'RETRY', available_at = now(), processed_at = null
                where id = ?
                """, work.requestId());
        worker.processClaimed(claimOne(medicaoId));

        assertThat(statusFor(medicaoId)).isEqualTo("COMPLETED");
        assertThat(count("atividade")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select progresso_atual from atividade", Integer.class)).isEqualTo(1);
        assertThat(countWhere("atividade_evento", "tipo = 'PROGRESSO'")).isEqualTo(1);
        assertThat(count("xp_lancamento")).isZero();
    }

    @Test
    void manualCompletionStaysInProgressWhenProgressIsFull() {
        seedAcademicContext("student-manual");
        seedEvent(true, BigDecimal.valueOf(20), true, EventoPoliticaAtribuicao.ALUNOS_VINCULADOS,
                MissaoCicloTipo.UNICA, 1, false);
        UUID medicaoId = ingest("msg-manual", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(medicaoId);

        assertThat(jdbcTemplate.queryForObject("select status from atividade", String.class)).isEqualTo("EM_ANDAMENTO");
        assertThat(jdbcTemplate.queryForObject("select progresso_atual from atividade", Integer.class)).isEqualTo(1);
        assertThat(countWhere("atividade", "started_at is not null and completed_at is null")).isEqualTo(1);
        assertThat(countWhere("atividade_evento", "tipo = 'CONCLUSAO'")).isZero();
        assertThat(count("xp_lancamento")).isZero();
    }

    @Test
    void unsupportedCycleAndPolicyFailPermanently() {
        seedAcademicContext("student-unsupported");
        seedEvent(true, BigDecimal.valueOf(20), true, EventoPoliticaAtribuicao.ALUNOS_VINCULADOS,
                MissaoCicloTipo.POR_PRESENCA, 1, true);
        UUID unsupportedCycle = ingest("msg-unsupported-cycle", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(unsupportedCycle);
        assertThat(statusFor(unsupportedCycle)).isEqualTo("FAILED");

        cleanDatabase();
        setUp();
        seedAcademicContext("student-policy");
        seedEvent(true, BigDecimal.valueOf(20), true, EventoPoliticaAtribuicao.CHECKIN_CONFIRMADO,
                MissaoCicloTipo.UNICA, 1, true);
        UUID unsupportedPolicy = ingest("msg-unsupported-policy", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(unsupportedPolicy);
        assertThat(statusFor(unsupportedPolicy)).isEqualTo("FAILED");
    }

    @Test
    void unmatchedEventCompletesWithoutOccurrenceAndNoApplicableEventIsIgnored() {
        seedEvent(true, BigDecimal.valueOf(30), true);
        UUID unmatchedMedicaoId = ingest("msg-unmatched", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(unmatchedMedicaoId);
        assertThat(statusFor(unmatchedMedicaoId)).isEqualTo("COMPLETED");
        assertThat(count("evento_ocorrencia")).isEqualTo(0);

        jdbcTemplate.update("update evento_definicao set ativo = false");
        UUID ignoredMedicaoId = ingest("msg-ignored", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(ignoredMedicaoId);
        assertThat(statusFor(ignoredMedicaoId)).isEqualTo("IGNORED");
    }

    @Test
    void retryDoesNotDuplicateOccurrenceOrEvidence() {
        seedEvent(true, BigDecimal.valueOf(20), true);
        UUID medicaoId = ingest("msg-retry", BigDecimal.valueOf(25)).response().medicaoId();
        var work = claimOne(medicaoId);

        worker.processClaimed(work);
        jdbcTemplate.update("""
                update evento_avaliacao_request
                set status = 'RETRY', available_at = now(), processed_at = null
                where id = ?
                """, work.requestId());
        var retry = claimOne(medicaoId);
        worker.processClaimed(retry);

        assertThat(statusFor(medicaoId)).isEqualTo("COMPLETED");
        assertThat(count("evento_ocorrencia")).isEqualTo(1);
        assertThat(count("evento_ocorrencia_evidencia")).isEqualTo(1);
    }

    @Test
    void academicAmbiguityFailsPermanently() {
        seedEvent(true, BigDecimal.valueOf(20), true);
        seedAmbiguousClasses();
        UUID ambiguousMedicaoId = ingest("msg-ambiguous", BigDecimal.valueOf(25)).response().medicaoId();

        processClaimed(ambiguousMedicaoId);
        assertThat(statusFor(ambiguousMedicaoId)).isEqualTo("FAILED");
    }

    private SensorIngestOrchestrator.IngestOutcome ingest(String messageId, BigDecimal value) {
        var outcome = orchestrator.ingest("producer", request(messageId, value));
        makeRequestAvailable(outcome);
        return outcome;
    }

    private void makeRequestAvailable(SensorIngestOrchestrator.IngestOutcome outcome) {
        if (outcome.response() != null && outcome.response().medicaoId() != null) {
            jdbcTemplate.update("update evento_avaliacao_request set available_at = now() where medicao_id = ?",
                    outcome.response().medicaoId());
        }
    }

    private SensorIngestOrchestrator.IngestOutcome ingestAt(String messageId, Instant timestamp, BigDecimal value) {
        Instant previous = measuredAt;
        measuredAt = timestamp;
        try {
            return ingest(messageId, value);
        } finally {
            measuredAt = previous;
        }
    }

    private SensorIngestOrchestrator.IngestOutcome ingestRawAt(String messageId, Instant timestamp, Map<String, Object> values) {
        Instant previous = measuredAt;
        measuredAt = timestamp;
        try {
            var outcome = orchestrator.ingest("producer", request(messageId, values));
            makeRequestAvailable(outcome);
            return outcome;
        } finally {
            measuredAt = previous;
        }
    }

    private void enableTemporal(boolean drools) {
        properties.getTemporalWindows().setEnabled(true);
        properties.getTemporalWindows().setDroolsEnabled(drools);
        properties.getTemporalWindows().setWorkerEnabled(false);
    }

    private void satisfyTemporalWindow(String prefix) {
        UUID start = ingestAt("msg-" + prefix + "-start", measuredAt, BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(start);
        UUID mid = ingestAt("msg-" + prefix + "-mid", measuredAt.plusSeconds(600), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(mid);
        UUID mid2 = ingestAt("msg-" + prefix + "-mid-2", measuredAt.plusSeconds(1200), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(mid2);
        UUID end = ingestAt("msg-" + prefix + "-end", measuredAt.plusSeconds(1800), BigDecimal.valueOf(25)).response().medicaoId();
        processClaimed(end);
        properties.getTemporalWindows().setWorkerEnabled(true);
        assertThat(temporalWorker.processAvailableBatch()).isGreaterThanOrEqualTo(1);
    }

    private void processClaimed() {
        var work = claimOne();
        worker.processClaimed(work);
    }

    private void processClaimed(UUID medicaoId) {
        var work = claimOne(medicaoId);
        worker.processClaimed(work);
    }

    private EventoAvaliacaoRequestService.EventoAvaliacaoWork claimOne() {
        for (int attempt = 0; attempt < 20; attempt++) {
            var claimed = requestService.claimAvailable(1, Duration.ofSeconds(30), 3);
            if (!claimed.isEmpty()) {
                return claimed.getFirst();
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for evaluation request", ex);
            }
        }
        throw new IllegalStateException("No evaluation request available for claim");
    }

    private EventoAvaliacaoRequestService.EventoAvaliacaoWork claimOne(UUID medicaoId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            var claimed = jdbcTemplate.query("""
                    update evento_avaliacao_request request
                    set status = 'PROCESSING',
                        attempts = request.attempts + 1,
                        claimed_at = now(),
                        lease_until = now() + interval '30 seconds',
                        processed_at = null,
                        last_error = null,
                        updated_at = now()
                    where request.id in (
                        select candidate.id
                        from evento_avaliacao_request candidate
                        where candidate.medicao_id = ?
                          and candidate.attempts < 3
                          and (
                                (
                                    candidate.status in ('PENDING','RETRY')
                                    and candidate.available_at <= now()
                                )
                                or (
                                    candidate.status = 'PROCESSING'
                                    and candidate.lease_until <= now()
                                )
                            )
                        for update of candidate skip locked
                        limit 1
                    )
                    returning request.id, request.medicao_id, request.attempts
                    """, (rs, rowNum) -> new EventoAvaliacaoRequestService.EventoAvaliacaoWork(
                    rs.getObject("id", UUID.class),
                    rs.getObject("medicao_id", UUID.class),
                    rs.getInt("attempts")
            ), medicaoId);
            if (!claimed.isEmpty()) {
                return claimed.getFirst();
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for evaluation request", ex);
            }
        }
        var rows = jdbcTemplate.queryForList("""
                select status, attempts, available_at, lease_until, processed_at, last_error
                from evento_avaliacao_request
                where medicao_id = ?
                """, medicaoId);
        throw new IllegalStateException("No evaluation request available for medicaoId=" + medicaoId + " rows=" + rows);
    }

    private SensorIngestDTOs.CanonicalIngestRequest request(String messageId, BigDecimal value) {
        return request(messageId, Map.of("temperature", value));
    }

    private SensorIngestDTOs.CanonicalIngestRequest request(String messageId, Map<String, Object> values) {
        return new SensorIngestDTOs.CanonicalIngestRequest(
                messageId + "-" + suffix,
                sensorId,
                measuredAt,
                MedicaoIngestaoSource.API,
                measuredAt,
                values
        );
    }

    private void seedSensor() {
        UUID predioId = UUID.randomUUID();
        String campus = "Campus-" + suffix;
        String unidade = "Unidade-" + suffix;
        jdbcTemplate.update("insert into campus (nome) values (?)", campus);
        jdbcTemplate.update("insert into unidade (nome) values (?)", unidade);
        jdbcTemplate.update("insert into predio (id, campus_id, nome) values (?, ?, ?)", predioId, campus, "Predio");
        jdbcTemplate.update("""
                insert into compartimento (id, predio_id, unidade_id, nome, tipo)
                values (?, ?, ?, 'Sala', 'Sala')
                """, roomId, predioId, unidade);
        jdbcTemplate.update("insert into tipo_de_sensor (nome) values (?)", tipoSensor);
        jdbcTemplate.update("""
                insert into sensor (external_id, nome, tipo_nome, compartimento_id, ativo)
                values (?, 'Sensor', ?, ?, true)
                """, sensorId, tipoSensor, roomId);
        jdbcTemplate.update("""
                insert into parametro_def (id, tipo_nome, nome, data_type, ativo)
                values (?, ?, 'temperature', 'NUMERIC', true)
                """, parametroDefId, tipoSensor);
    }

    private UUID seedParametroDef(String nome, String dataType) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into parametro_def (id, tipo_nome, nome, data_type, ativo)
                values (?, ?, ?, ?, true)
                """, id, tipoSensor, nome, dataType);
        return id;
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table
                    atividade_evento,
                    xp_lancamento,
                    atividade,
                    evento_janela_evidencia,
                    evento_janela_avaliacao,
                    evento_ocorrencia_evidencia,
                    evento_ocorrencia,
                    evento_avaliacao_request,
                    aluno_disciplina,
                    pessoa_role,
                    pessoa,
                    medicao_ingestao_metadata,
                    sensor_integration_value_mapping,
                    sensor_integration_binding,
                    sensor_integration_parser_version,
                    sensor_integration_profile,
                    parametro_valor,
                    medicao,
                    evento_condicao,
                    evento_definicao,
                    missao,
                    periodo_aula,
                    disciplina,
                    sensor,
                    parametro_def,
                    tipo_de_sensor,
                    compartimento,
                    predio,
                    unidade,
                    campus
                restart identity cascade
                """);
    }

    private IntegrationProfileIds seedActiveProfile() {
        UUID profileId = UUID.randomUUID();
        UUID parserVersionId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into sensor_integration_profile
                (id, nome, descricao, source, ativo, created_at, updated_at)
                values (?, ?, 'REST profile', 'REST', true, now(), now())
                """, profileId, "Profile-" + suffix);
        jdbcTemplate.update("""
                insert into sensor_integration_parser_version
                (id, profile_id, version, status, sensor_resolution_mode, message_id_pointer,
                 sensor_external_id_pointer, timestamp_pointer, timestamp_format, created_at, updated_at, published_at)
                values (?, ?, 1, 'ACTIVE', 'PAYLOAD_POINTER', '/messageId',
                        '/sensorExternalId', '/timestamp', 'ISO_INSTANT', now(), now(), now())
                """, parserVersionId, profileId);
        jdbcTemplate.update("""
                insert into sensor_integration_binding
                (id, sensor_external_id, profile_id, ativo, created_at)
                values (?, ?, ?, true, now())
                """, UUID.randomUUID(), sensorId, profileId);
        return new IntegrationProfileIds(profileId, parserVersionId);
    }

    private record IntegrationProfileIds(UUID profileId, UUID parserVersionId) {}

    private void seedEvent(boolean missionActive, BigDecimal threshold, boolean conditionActive) {
        seedEvent(missionActive, threshold, conditionActive, EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA,
                MissaoCicloTipo.UNICA, 1, true);
    }

    private void seedEvent(
            boolean missionActive,
            BigDecimal threshold,
            boolean conditionActive,
            EventoPoliticaAtribuicao politica,
            MissaoCicloTipo cicloTipo,
            int progressoNecessario,
            boolean conclusaoAutomatica
    ) {
        missionId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        UUID conditionId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into missao
                (id, titulo, descricao, tipo, value, ativo, created_at, ciclo_tipo, progresso_necessario, conclusao_automatica)
                values (?, 'Missao', 'Descricao', 'Individual', 10, ?, now(), ?, ?, ?)
                """, missionId, missionActive, cicloTipo.name(), progressoNecessario, conclusaoAutomatica);
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'Temperatura alta', 'MEDICAO_RECEBIDA', 'INSTANTANEO', 'ALL',
                        ?, 1, 1, true, now(), now())
                """, eventId, missionId, politica.name());
        jdbcTemplate.update("""
                insert into evento_condicao
                (id, evento_definicao_id, parametro_def_id, operador, valor_numeric_1,
                 agregacao, obrigatoria, ordem, ativo, created_at)
                values (?, ?, ?, 'GT', ?, 'ULTIMO', true, 1, ?, now())
                """, conditionId, eventId, parametroDefId, threshold, conditionActive);
    }

    private void seedDurationEvent(BigDecimal threshold, boolean conditionActive, int janelaSegundos, int duracaoMinimaSegundos) {
        seedDurationEvent(threshold, conditionActive, janelaSegundos, duracaoMinimaSegundos,
                EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA, MissaoCicloTipo.UNICA, 1, true, 10);
    }

    private void seedDurationEvent(
            BigDecimal threshold,
            boolean conditionActive,
            int janelaSegundos,
            int duracaoMinimaSegundos,
            EventoPoliticaAtribuicao politica,
            MissaoCicloTipo cicloTipo,
            int progressoNecessario,
            boolean conclusaoAutomatica,
            int value
    ) {
        missionId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        UUID conditionId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into missao
                (id, titulo, descricao, tipo, value, ativo, created_at, ciclo_tipo, progresso_necessario, conclusao_automatica)
                values (?, 'Missao temporal', 'Descricao', 'Individual', ?, true, now(), ?, ?, ?)
                """, missionId, value, cicloTipo.name(), progressoNecessario, conclusaoAutomatica);
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 janela_segundos, duracao_minima_segundos, quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'Temperatura sustentada', 'MEDICAO_RECEBIDA', 'DURACAO', 'ALL',
                        ?, ?, ?, 1, 1, true, now(), now())
                """, eventId, missionId, politica.name(), janelaSegundos, duracaoMinimaSegundos);
        jdbcTemplate.update("""
                insert into evento_condicao
                (id, evento_definicao_id, parametro_def_id, operador, valor_numeric_1,
                 agregacao, obrigatoria, ordem, ativo, created_at)
                values (?, ?, ?, 'GT', ?, 'ULTIMO', true, 1, ?, now())
                """, conditionId, eventId, parametroDefId, threshold, conditionActive);
    }

    private void seedTransitionEvent(
            String parametroNome,
            UUID parametroId,
            String dataType,
            String operador,
            BigDecimal numericValue,
            Boolean booleanValue,
            String textValue
    ) {
        seedTransitionEvent(parametroNome, parametroId, dataType, operador, numericValue, booleanValue, textValue,
                EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA, MissaoCicloTipo.UNICA, 1, true, 10);
    }

    private void seedTransitionEvent(
            String parametroNome,
            UUID parametroId,
            String dataType,
            String operador,
            BigDecimal numericValue,
            Boolean booleanValue,
            String textValue,
            EventoPoliticaAtribuicao politica,
            MissaoCicloTipo cicloTipo,
            int progressoNecessario,
            boolean conclusaoAutomatica,
            int value
    ) {
        missionId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        UUID conditionId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into missao
                (id, titulo, descricao, tipo, value, ativo, created_at, ciclo_tipo, progresso_necessario, conclusao_automatica)
                values (?, 'Missao transicao', 'Descricao', 'Individual', ?, true, now(), ?, ?, ?)
                """, missionId, value, cicloTipo.name(), progressoNecessario, conclusaoAutomatica);
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, ?, 'MEDICAO_RECEBIDA', 'TRANSICAO', 'ALL',
                        ?, 1, 1, true, now(), now())
                """, eventId, missionId, "Transicao " + parametroNome, politica.name());
        jdbcTemplate.update("""
                insert into evento_condicao
                (id, evento_definicao_id, parametro_def_id, operador, valor_numeric_1, valor_boolean, valor_text,
                 agregacao, obrigatoria, ordem, ativo, created_at)
                values (?, ?, ?, ?, ?, ?, ?, 'ULTIMO', true, 1, true, now())
                """, conditionId, eventId, parametroId, operador, numericValue, booleanValue, textValue);
    }

    private void seedWindowClosedEvent(
            BigDecimal threshold,
            EventoPoliticaAtribuicao politica,
            MissaoCicloTipo cicloTipo,
            int progressoNecessario,
            boolean conclusaoAutomatica,
            int value
    ) {
        missionId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        UUID conditionId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into missao
                (id, titulo, descricao, tipo, value, ativo, created_at, ciclo_tipo, progresso_necessario, conclusao_automatica)
                values (?, 'Missao janela encerrada', 'Descricao', 'Individual', ?, true, now(), ?, ?, ?)
                """, missionId, value, cicloTipo.name(), progressoNecessario, conclusaoAutomatica);
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 janela_segundos, quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'Janela encerrada', 'JANELA_ENCERRADA', 'INSTANTANEO', 'ALL',
                        ?, 120, 1, 1, true, now(), now())
                """, eventId, missionId, politica.name());
        jdbcTemplate.update("""
                insert into evento_condicao
                (id, evento_definicao_id, parametro_def_id, operador, valor_numeric_1,
                 agregacao, obrigatoria, ordem, ativo, created_at)
                values (?, ?, ?, 'GT', ?, 'ULTIMO', true, 1, true, now())
                """, conditionId, eventId, parametroDefId, threshold);
    }

    private void seedAcademicContext(String... pessoaIds) {
        jdbcTemplate.update("""
                insert into disciplina (id, nome, unidade_sigla)
                values (?, 'Disciplina', 'UNI')
                """, disciplinaId);
        jdbcTemplate.update("""
                insert into periodo_aula
                (id, compartimento_id, data, turno, periodo_aula, hora_inicio, hora_fim, tipo, descricao,
                 disciplina_id, turma, sincronizado_em)
                values (?, ?, date '2026-09-13', 1, 1, time '06:00', time '08:00', 'AULA', 'Aula',
                        ?, 'T1', now())
                """, UUID.randomUUID(), roomId, disciplinaId);
        for (String pessoaId : pessoaIds) {
            jdbcTemplate.update("""
                    insert into pessoa (id, nome, email, password, created_at)
                    values (?, ?, ?, 'hash', now())
                    """, pessoaId, pessoaId, pessoaId + "-" + suffix + "@example.com");
            jdbcTemplate.update("""
                    insert into aluno_disciplina
                    (id, pessoa_id, disciplina_id, turma, periodo_letivo, status, vinculado_em)
                    values (?, ?, ?, 'T1', '2026/1', 'ATIVA', now())
                    """, UUID.randomUUID(), pessoaId, disciplinaId);
        }
    }

    private void seedAmbiguousClasses() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into periodo_aula
                (id, compartimento_id, data, turno, periodo_aula, hora_inicio, hora_fim, tipo, descricao, sincronizado_em)
                values (?, ?, date '2026-09-13', 1, 1, time '07:00', time '08:00', 'AULA', 'Aula 1', now())
                """, first, roomId);
        jdbcTemplate.update("""
                insert into periodo_aula
                (id, compartimento_id, data, turno, periodo_aula, hora_inicio, hora_fim, tipo, descricao, sincronizado_em)
                values (?, ?, date '2026-09-13', 1, 2, time '07:00', time '08:00', 'AULA', 'Aula 2', now())
                """, second, roomId);
    }

    private long count(String table) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }

    private long countWhere(String table, String predicate) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + table + " where " + predicate, Long.class);
        return count == null ? 0 : count;
    }

    private long requestCountFor(UUID medicaoId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from evento_avaliacao_request where medicao_id = ?",
                Long.class,
                medicaoId
        );
        return count == null ? 0 : count;
    }

    private String statusFor(UUID medicaoId) {
        return jdbcTemplate.queryForObject(
                "select status from evento_avaliacao_request where medicao_id = ?",
                String.class,
                medicaoId
        );
    }

    private String lastErrorFor(UUID medicaoId) {
        String lastError = jdbcTemplate.queryForObject(
                "select last_error from evento_avaliacao_request where medicao_id = ?",
                String.class,
                medicaoId
        );
        return lastError == null ? "last_error=null" : "last_error=" + lastError;
    }
}
