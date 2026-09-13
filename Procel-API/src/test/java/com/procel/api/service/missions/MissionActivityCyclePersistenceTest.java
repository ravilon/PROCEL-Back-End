package com.procel.api.service.missions;

import com.procel.api.dto.missions.MissaoDTOs;
import com.procel.api.entity.missions.AtividadeEvento;
import com.procel.api.entity.missions.AtividadeEventoTipo;
import com.procel.api.entity.missions.AtividadeStatus;
import com.procel.api.entity.missions.MissaoCicloTipo;
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

import java.sql.Timestamp;
import java.time.Instant;
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
class MissionActivityCyclePersistenceTest {

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
    @Autowired MissaoService missaoService;
    @Autowired MissionActivityCycleService cycleService;

    String pessoaId;
    UUID missaoId;
    UUID eventoOcorrenciaId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        String suffix = UUID.randomUUID().toString();
        pessoaId = "pessoa-" + suffix;
        missaoId = UUID.randomUUID();
        eventoOcorrenciaId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into pessoa (id, nome, email, password, created_at)
                values (?, 'Pessoa', ?, 'hash', now())
                """, pessoaId, pessoaId + "@example.com");
        jdbcTemplate.update("""
                insert into missao
                (id, titulo, descricao, tipo, value, ativo, created_at, ciclo_tipo, progresso_necessario, conclusao_automatica)
                values (?, 'Missao diaria', 'Descricao', 'Individual', 10, true, now(), 'DIARIA', 3, true)
                """, missaoId);
        seedOccurrence();
    }

    @Test
    void missionDefaultsAndValidationAreApplied() {
        var response = missaoService.createMissao(new MissaoDTOs.CreateMissaoRequest(
                "Missao default",
                "Descricao",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(response.cicloTipo()).isEqualTo(MissaoCicloTipo.UNICA);
        assertThat(response.progressoNecessario()).isEqualTo(1);
        assertThat(response.conclusaoAutomatica()).isTrue();

        assertThatThrownBy(() -> missaoService.createMissao(new MissaoDTOs.CreateMissaoRequest(
                "Missao invalida", null, null, null, true, null, MissaoCicloTipo.DIARIA, 0, true
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void samePersonCanReceiveSameMissionInDifferentCyclesAndSameKeyIsIdempotent() {
        var first = createActivity("DIA:2026-09-13", Instant.parse("2026-09-13T03:00:00Z"));
        var duplicate = createActivity("DIA:2026-09-13", Instant.parse("2026-09-13T03:00:00Z"));
        var secondCycle = createActivity("DIA:2026-09-14", Instant.parse("2026-09-14T03:00:00Z"));

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(secondCycle.getId()).isNotEqualTo(first.getId());
        assertThat(first.getStatus()).isEqualTo(AtividadeStatus.PENDENTE);
        assertThat(first.getProgressoAtual()).isZero();
        assertThat(first.getProgressoNecessario()).isEqualTo(3);
        assertThat(count("atividade")).isEqualTo(2);
    }

    @Test
    void concurrentCreationDoesNotDuplicateActivity() throws Exception {
        var command = command("DIA:2026-09-13", Instant.parse("2026-09-13T03:00:00Z"));
        List<Object> results = runConcurrently(
                () -> cycleService.localizarOuCriar(command),
                () -> cycleService.localizarOuCriar(command)
        );

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(com.procel.api.entity.missions.Atividade.class));
        assertThat(count("atividade")).isEqualTo(1);
    }

    @Test
    void existingCycleWithIncompatibleConfigurationConflicts() {
        createActivity("DIA:2026-09-13", Instant.parse("2026-09-13T03:00:00Z"));

        assertThatThrownBy(() -> cycleService.localizarOuCriar(new MissionActivityCycleService.CreateCycleActivityCommand(
                pessoaId,
                missaoId,
                "DIA:2026-09-13",
                MissaoCicloTipo.SEMANAL,
                Instant.parse("2026-09-13T03:00:00Z"),
                Instant.parse("2026-09-14T03:00:00Z")
        ))).isInstanceOf(ConflictException.class);
    }

    @Test
    void activityEventRegistrationIsIdempotentAndDetectsConflict() {
        var atividade = createActivity("DIA:2026-09-13", Instant.parse("2026-09-13T03:00:00Z"));
        var command = new MissionActivityCycleService.RegisterActivityEventCommand(
                atividade.getId(),
                eventoOcorrenciaId,
                AtividadeEventoTipo.PROGRESSO,
                1,
                Instant.parse("2026-09-13T10:00:00Z")
        );

        AtividadeEvento first = cycleService.registrarEvento(command);
        AtividadeEvento duplicate = cycleService.registrarEvento(command);

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(count("atividade_evento")).isEqualTo(1);

        assertThatThrownBy(() -> cycleService.registrarEvento(new MissionActivityCycleService.RegisterActivityEventCommand(
                atividade.getId(),
                eventoOcorrenciaId,
                AtividadeEventoTipo.PROGRESSO,
                2,
                Instant.parse("2026-09-13T10:05:00Z")
        ))).isInstanceOf(ConflictException.class);
    }

    @Test
    void databaseConstraintsAndIndexesExist() {
        assertThat(indexExists("uk_atividade_pessoa_missao_ciclo")).isTrue();
        assertThat(indexExists("ix_atividade_missao_ciclo_status")).isTrue();
        assertThat(indexExists("uk_atividade_evento_ocorrencia_tipo")).isTrue();
        assertThat(indexExists("ix_atividade_evento_ocorrencia")).isTrue();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into atividade
                (id, pessoa_id, missao_id, status, assigned_at, chave_ciclo, ciclo_tipo,
                 progresso_atual, progresso_necessario, conclusao_automatica)
                values (?, ?, ?, 'PENDENTE', now(), 'BAD', 'DIARIA', -1, 1, true)
                """, UUID.randomUUID(), pessoaId, missaoId)).isInstanceOf(RuntimeException.class);
    }

    private com.procel.api.entity.missions.Atividade createActivity(String chave, Instant inicio) {
        return cycleService.localizarOuCriar(command(chave, inicio));
    }

    private MissionActivityCycleService.CreateCycleActivityCommand command(String chave, Instant inicio) {
        return new MissionActivityCycleService.CreateCycleActivityCommand(
                pessoaId,
                missaoId,
                chave,
                MissaoCicloTipo.DIARIA,
                inicio,
                inicio.plusSeconds(86400)
        );
    }

    private void seedOccurrence() {
        String suffix = UUID.randomUUID().toString();
        String compartimentoId = "ROOM-" + suffix;
        String tipoSensor = "TYPE-" + suffix;
        String sensorId = "SENSOR-" + suffix;
        UUID predioId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("insert into campus (nome) values (?)", "Campus-" + suffix);
        jdbcTemplate.update("insert into unidade (nome) values (?)", "Unidade-" + suffix);
        jdbcTemplate.update("insert into predio (id, campus_id, nome) values (?, ?, ?)", predioId, "Campus-" + suffix, "Predio");
        jdbcTemplate.update("""
                insert into compartimento (id, predio_id, unidade_id, nome, tipo)
                values (?, ?, ?, 'Sala', 'Sala')
                """, compartimentoId, predioId, "Unidade-" + suffix);
        jdbcTemplate.update("insert into tipo_de_sensor (nome) values (?)", tipoSensor);
        jdbcTemplate.update("""
                insert into sensor (external_id, nome, tipo_nome, compartimento_id, ativo)
                values (?, 'Sensor', ?, ?, true)
                """, sensorId, tipoSensor, compartimentoId);
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'Evento', 'MEDICAO_RECEBIDA', 'INSTANTANEO', 'ALL',
                        'SEM_ATRIBUICAO_AUTOMATICA', 1, 1, true, now(), now())
                """, eventId, missaoId);
        jdbcTemplate.update("""
                insert into evento_ocorrencia
                (id, evento_definicao_id, compartimento_id, sensor_external_id, status, inicio_em, fim_em,
                 detectado_em, chave_idempotencia, contexto_snapshot, conteudo_fingerprint, created_at, updated_at)
                values (?, ?, ?, ?, 'CONFIRMADO', ?, ?, ?, ?, '{}'::jsonb, repeat('a', 64), now(), now())
                """,
                eventoOcorrenciaId,
                eventId,
                compartimentoId,
                sensorId,
                Timestamp.from(Instant.parse("2026-09-13T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-13T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-13T10:00:01Z")),
                "event-key-" + suffix);
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table
                    xp_lancamento,
                    atividade_evento,
                    evento_ocorrencia_evidencia,
                    evento_ocorrencia,
                    evento_condicao,
                    evento_definicao,
                    atividade,
                    missao,
                    pessoa_role,
                    pessoa,
                    parametro_valor,
                    medicao,
                    parametro_def,
                    sensor,
                    tipo_de_sensor,
                    periodo_aula,
                    compartimento,
                    predio,
                    unidade,
                    campus
                cascade
                """);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
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

    private List<Object> runConcurrently(
            Callable<com.procel.api.entity.missions.Atividade> first,
            Callable<com.procel.api.entity.missions.Atividade> second
    ) throws Exception {
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
            Callable<com.procel.api.entity.missions.Atividade> task,
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
