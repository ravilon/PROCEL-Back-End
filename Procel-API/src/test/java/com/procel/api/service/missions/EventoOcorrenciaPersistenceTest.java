package com.procel.api.service.missions;

import com.procel.api.entity.missions.EventoAvaliacaoRequestStatus;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoOcorrenciaEvidenciaPapel;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventoOcorrenciaPersistenceTest {

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

    @Autowired EventoOcorrenciaService ocorrenciaService;
    @Autowired EventoAvaliacaoRequestService avaliacaoRequestService;
    @Autowired JdbcTemplate jdbcTemplate;

    UUID eventoId;
    String compartimentoId;
    UUID periodoAulaId;
    String sensorExternalId;
    UUID medicaoId;
    UUID medicao2Id;
    UUID parametroValorId;
    UUID parametroValorOutraMedicaoId;

    Instant start = Instant.parse("2026-09-13T10:00:00Z");
    Instant end = Instant.parse("2026-09-13T10:05:00Z");
    Instant detected = Instant.parse("2026-09-13T10:00:10Z");

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        compartimentoId = "ROOM-" + suffix;
        sensorExternalId = "SENSOR-" + suffix;
        eventoId = UUID.randomUUID();
        periodoAulaId = UUID.randomUUID();
        medicaoId = UUID.randomUUID();
        medicao2Id = UUID.randomUUID();
        parametroValorId = UUID.randomUUID();
        parametroValorOutraMedicaoId = UUID.randomUUID();

        UUID predioId = UUID.randomUUID();
        UUID missaoId = UUID.randomUUID();
        UUID parametroDefId = UUID.randomUUID();
        String campus = "Campus-" + suffix;
        String unidade = "Unidade-" + suffix;
        String tipoSensor = "TYPE-" + suffix;

        jdbcTemplate.update("insert into campus (nome) values (?)", campus);
        jdbcTemplate.update("insert into unidade (nome) values (?)", unidade);
        jdbcTemplate.update("insert into predio (id, campus_id, nome) values (?, ?, ?)", predioId, campus, "Predio");
        jdbcTemplate.update("""
                insert into compartimento (id, predio_id, unidade_id, nome, tipo)
                values (?, ?, ?, ?, ?)
                """, compartimentoId, predioId, unidade, "Sala", "Sala");
        jdbcTemplate.update("insert into tipo_de_sensor (nome) values (?)", tipoSensor);
        jdbcTemplate.update("""
                insert into sensor (external_id, nome, tipo_nome, compartimento_id, ativo)
                values (?, ?, ?, ?, true)
                """, sensorExternalId, "Sensor", tipoSensor, compartimentoId);
        jdbcTemplate.update("""
                insert into missao (id, titulo, descricao, tipo, value, ativo, created_at)
                values (?, 'Missao', 'Descricao', 'Individual', 10, true, now())
                """, missaoId);
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'Evento', 'MEDICAO_RECEBIDA', 'INSTANTANEO', 'ALL',
                        'SEM_ATRIBUICAO_AUTOMATICA', 1, 1, true, now(), now())
                """, eventoId, missaoId);
        jdbcTemplate.update("""
                insert into periodo_aula
                (id, compartimento_id, data, turno, periodo_aula, hora_inicio, hora_fim, tipo, descricao, sincronizado_em)
                values (?, ?, date '2026-09-13', 1, 1, time '10:00', time '10:50', 'AULA', 'Aula', now())
                """, periodoAulaId, compartimentoId);
        jdbcTemplate.update("""
                insert into parametro_def (id, tipo_nome, nome, data_type, ativo)
                values (?, ?, 'temperature', 'NUMERIC', true)
                """, parametroDefId, tipoSensor);
        insertMeasurement(medicaoId);
        insertMeasurement(medicao2Id);
        jdbcTemplate.update("""
                insert into parametro_valor (id, medicao_id, parametro_def_id, numeric_value)
                values (?, ?, ?, 25.0)
                """, parametroValorId, medicaoId, parametroDefId);
        jdbcTemplate.update("""
                insert into parametro_valor (id, medicao_id, parametro_def_id, numeric_value)
                values (?, ?, ?, 26.0)
                """, parametroValorOutraMedicaoId, medicao2Id, parametroDefId);
    }

    @Test
    void createsOccurrenceAndTreatsEquivalentIdempotencyAsSuccess() {
        var command = command("key-create", snapshot("{\"b\":2,\"a\":1}"));

        EventoOcorrencia first = ocorrenciaService.registrarOcorrencia(command);
        EventoOcorrencia second = ocorrenciaService.registrarOcorrencia(command("key-create", snapshot("{\"a\":1,\"b\":2}")));

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getStatus()).isEqualTo(EventoOcorrenciaStatus.DETECTADO);
        assertThat(first.getContextoSnapshot()).containsPattern("\"a\"\\s*:\\s*1");
        assertThat(first.getContextoSnapshot()).containsPattern("\"b\"\\s*:\\s*2");
    }

    @Test
    void repeatedKeyWithDivergentContentConflicts() {
        ocorrenciaService.registrarOcorrencia(command("key-divergent", snapshot("{\"a\":1}")));

        assertThatThrownBy(() -> ocorrenciaService.registrarOcorrencia(command("key-divergent", snapshot("{\"a\":2}"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsInvalidInterval() {
        assertThatThrownBy(() -> ocorrenciaService.registrarOcorrencia(new EventoOcorrenciaService.RegistrarOcorrenciaCommand(
                eventoId, compartimentoId, periodoAulaId, sensorExternalId, end, start, detected, "key-invalid", snapshot("{}")
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsValidTransitionsAndRejectsInvalidOrFinalTransitions() {
        EventoOcorrencia occurrence = ocorrenciaService.registrarOcorrencia(command("key-transition", snapshot("{}")));

        EventoOcorrencia confirmed = ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.CONFIRMADO);
        assertThat(confirmed.getStatus()).isEqualTo(EventoOcorrenciaStatus.CONFIRMADO);
        EventoOcorrencia processed = ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.PROCESSADO);
        assertThat(processed.getStatus()).isEqualTo(EventoOcorrenciaStatus.PROCESSADO);

        assertThatThrownBy(() -> ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.INVALIDADO))
                .isInstanceOf(ConflictException.class);

        EventoOcorrencia invalidCandidate = ocorrenciaService.registrarOcorrencia(command("key-invalid-transition", snapshot("{}")));
        assertThatThrownBy(() -> ocorrenciaService.atualizarStatus(invalidCandidate.getId(), EventoOcorrenciaStatus.PROCESSADO))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void appendsMultipleEvidenceIdempotentlyAndValidatesMeasurementOwnership() {
        EventoOcorrencia occurrence = ocorrenciaService.registrarOcorrencia(command("key-evidence", snapshot("{}")));

        var withValue = ocorrenciaService.anexarEvidencia(new EventoOcorrenciaService.AnexarEvidenciaCommand(
                occurrence.getId(), medicaoId, parametroValorId, EventoOcorrenciaEvidenciaPapel.CONDICAO
        ));
        var duplicate = ocorrenciaService.anexarEvidencia(new EventoOcorrenciaService.AnexarEvidenciaCommand(
                occurrence.getId(), medicaoId, parametroValorId, EventoOcorrenciaEvidenciaPapel.CONDICAO
        ));
        var measurementOnly = ocorrenciaService.anexarEvidencia(new EventoOcorrenciaService.AnexarEvidenciaCommand(
                occurrence.getId(), medicao2Id, null, EventoOcorrenciaEvidenciaPapel.INICIO
        ));

        assertThat(withValue.getId()).isEqualTo(duplicate.getId());
        assertThat(measurementOnly.getParametroValor()).isNull();
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from evento_ocorrencia_evidencia where evento_ocorrencia_id = ?",
                Integer.class,
                occurrence.getId()
        );
        assertThat(count).isEqualTo(2);

        assertThatThrownBy(() -> ocorrenciaService.anexarEvidencia(new EventoOcorrenciaService.AnexarEvidenciaCommand(
                occurrence.getId(), medicaoId, parametroValorOutraMedicaoId, EventoOcorrenciaEvidenciaPapel.BASELINE
        ))).isInstanceOf(ConflictException.class);
    }

    @Test
    void createsPendingEvaluationRequestOnlyOnceWithDefaults() {
        var first = avaliacaoRequestService.criarPendente(medicaoId);
        var second = avaliacaoRequestService.criarPendente(medicaoId);

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getStatus()).isEqualTo(EventoAvaliacaoRequestStatus.PENDING);
        assertThat(first.getAttempts()).isZero();
        assertThat(first.getAvailableAt()).isNotNull();
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(first.getUpdatedAt()).isNotNull();
        assertThat(first.getProcessedAt()).isNull();
    }

    @Test
    void databaseConstraintsAndIndexesExist() {
        assertThat(indexExists("ux_evento_ocorrencia_chave_idempotencia")).isTrue();
        assertThat(indexExists("ux_evento_evidencia_parametro_valor")).isTrue();
        assertThat(indexExists("ix_evento_avaliacao_request_status_available")).isTrue();
        assertThat(indexExists("ix_evento_avaliacao_request_lease")).isTrue();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into evento_ocorrencia
                (evento_definicao_id, compartimento_id, status, inicio_em, detectado_em,
                 chave_idempotencia, contexto_snapshot, conteudo_fingerprint, created_at, updated_at)
                values (?, ?, 'DETECTADO', now(), now(), 'bad-fk', '{}'::jsonb, repeat('a', 64), now(), now())
                """, UUID.randomUUID(), compartimentoId)).isInstanceOf(RuntimeException.class);

        jdbcTemplate.update("""
                insert into evento_avaliacao_request
                (medicao_id, status, attempts, available_at, processed_at, created_at, updated_at)
                values (?, 'COMPLETED', 0, now(), now(), now(), now())
                """, medicao2Id);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into evento_avaliacao_request
                (medicao_id, status, attempts, available_at, created_at, updated_at)
                values (?, 'PENDING', 0, now(), now(), now())
                """, medicao2Id)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void concurrentEquivalentRegistrationsReturnSameOccurrence() throws Exception {
        var command = command("key-concurrent-equivalent", snapshot("{\"a\":1}"));
        List<Object> results = runConcurrently(
                () -> ocorrenciaService.registrarOcorrencia(command),
                () -> ocorrenciaService.registrarOcorrencia(command)
        );

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(EventoOcorrencia.class));
        assertThat(((EventoOcorrencia) results.get(0)).getId()).isEqualTo(((EventoOcorrencia) results.get(1)).getId());
    }

    @Test
    void concurrentDivergentRegistrationsHaveOneSuccessAndOneConflict() throws Exception {
        List<Object> results = runConcurrently(
                () -> ocorrenciaService.registrarOcorrencia(command("key-concurrent-divergent", snapshot("{\"a\":1}"))),
                () -> ocorrenciaService.registrarOcorrencia(command("key-concurrent-divergent", snapshot("{\"a\":2}")))
        );

        assertThat(results.stream().filter(EventoOcorrencia.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(ConflictException.class::isInstance)).hasSize(1);
    }

    private void insertMeasurement(UUID id) {
        jdbcTemplate.update("""
                insert into medicao (id, sensor_external_id, timestamp, recebido_em, source)
                values (?, ?, ?, ?, 'test')
                """, id, sensorExternalId, Timestamp.from(start), Timestamp.from(detected));
    }

    private EventoOcorrenciaService.RegistrarOcorrenciaCommand command(String key, String snapshot) {
        return new EventoOcorrenciaService.RegistrarOcorrenciaCommand(
                eventoId,
                compartimentoId,
                periodoAulaId,
                sensorExternalId,
                start,
                end,
                detected,
                key,
                snapshot
        );
    }

    private String snapshot(String extraJson) {
        return """
                {
                  "eventDefinitionId":"%s",
                  "nome":"Evento",
                  "tipoDisparo":"MEDICAO_RECEBIDA",
                  "modoAvaliacao":"INSTANTANEO",
                  "operadorLogico":"ALL",
                  "politicaAtribuicao":"SEM_ATRIBUICAO_AUTOMATICA",
                  "conditions":[],
                  "compartimentoId":"%s",
                  "periodoAulaId":"%s",
                  "periodoLetivo":"2026/1",
                  "eligiblePersonIds":["1","2"],
                  "evaluationTime":"2026-09-13T10:00:10Z",
                  "extra":%s
                }
                """.formatted(eventoId, compartimentoId, periodoAulaId, extraJson);
    }

    private boolean indexExists(String indexName) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists (
                    select 1
                    from pg_indexes
                    where schemaname = 'public'
                      and indexname = ?
                )
                """, Boolean.class, indexName);
        return Boolean.TRUE.equals(exists);
    }

    private List<Object> runConcurrently(Callable<EventoOcorrencia> first, Callable<EventoOcorrencia> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> tasks = List.of(
                    concurrentTask(first, ready, startTogether),
                    concurrentTask(second, ready, startTogether)
            );
            List<java.util.concurrent.Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(task));
            }
            ready.await();
            startTogether.countDown();
            List<Object> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Object> concurrentTask(
            Callable<EventoOcorrencia> task,
            CountDownLatch ready,
            CountDownLatch startTogether
    ) {
        return () -> {
            ready.countDown();
            startTogether.await();
            try {
                return task.call();
            } catch (Exception ex) {
                return ex;
            }
        };
    }
}
