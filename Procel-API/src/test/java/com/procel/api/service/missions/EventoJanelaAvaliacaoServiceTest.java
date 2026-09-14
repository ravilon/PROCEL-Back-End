package com.procel.api.service.missions;

import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.entity.missions.EventoJanelaAvaliacao;
import com.procel.api.entity.missions.EventoJanelaEvidenciaPapel;
import com.procel.api.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
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
class EventoJanelaAvaliacaoServiceTest {

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
        registry.add("procel.missions.evaluation.temporal-windows.maximum-sample-gap", () -> "5m");
        registry.add("procel.missions.evaluation.temporal-windows.maximum-window-duration", () -> "24h");
    }

    @Autowired EventoJanelaAvaliacaoService service;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired MissionEvaluationProperties properties;

    UUID eventoId;
    String compartimentoId;
    UUID periodoAulaId;
    String sensorExternalId;
    UUID medicaoId;
    UUID parametroValorId;
    Instant start = Instant.parse("2026-09-13T10:00:00Z");
    Instant end = Instant.parse("2026-09-13T10:30:00Z");
    Instant next = Instant.parse("2026-09-13T10:30:00Z");

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from evento_janela_evidencia");
        jdbcTemplate.update("delete from evento_janela_avaliacao");

        String suffix = UUID.randomUUID().toString();
        compartimentoId = "ROOM-WINDOW-" + suffix;
        sensorExternalId = "SENSOR-WINDOW-" + suffix;
        eventoId = UUID.randomUUID();
        periodoAulaId = UUID.randomUUID();
        medicaoId = UUID.randomUUID();
        parametroValorId = UUID.randomUUID();

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
                 janela_segundos, duracao_minima_segundos, quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'Evento temporal', 'MEDICAO_RECEBIDA', 'DURACAO', 'ALL',
                        'SEM_ATRIBUICAO_AUTOMATICA', 1800, 1800, 1, 1, true, now(), now())
                """, eventoId, missaoId);
        jdbcTemplate.update("""
                insert into periodo_aula
                (id, compartimento_id, data, turno, periodo_aula, hora_inicio, hora_fim, tipo, descricao, sincronizado_em)
                values (?, ?, date '2026-09-13', 1, 1, time '10:00', time '10:50', 'AULA', 'Aula', now())
                """, periodoAulaId, compartimentoId);
        jdbcTemplate.update("""
                insert into parametro_def (id, tipo_nome, nome, data_type, ativo)
                values (?, ?, 'presence', 'BOOLEAN', true)
                """, parametroDefId, tipoSensor);
        jdbcTemplate.update("""
                insert into medicao (id, sensor_external_id, timestamp, recebido_em, source)
                values (?, ?, ?, ?, 'test')
                """, medicaoId, sensorExternalId, Timestamp.from(start), Timestamp.from(start));
        jdbcTemplate.update("""
                insert into parametro_valor (id, medicao_id, parametro_def_id, boolean_value)
                values (?, ?, ?, true)
                """, parametroValorId, medicaoId, parametroDefId);
    }

    @Test
    void aberturaIdempotenteRecuperaMesmaJanela() {
        EventoJanelaAvaliacao first = service.abrir(command("window-idempotent", start, end));
        EventoJanelaAvaliacao second = service.abrir(command("window-idempotent", start, end));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(status(first.getId())).isEqualTo("ABERTA");
    }

    @Test
    void conflitoNaMesmaChaveComConteudoDivergente() {
        service.abrir(command("window-conflict", start, end));

        assertThatThrownBy(() -> service.abrir(command("window-conflict", start.plusSeconds(1), end)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void atualizacaoRespeitaIntervaloSemiaberto() {
        UUID janelaId = service.abrir(command("window-update", start, end)).getId();

        assertThat(service.atualizarMedicao(janelaId, start.plusSeconds(60), start.plusSeconds(120))).isTrue();
        assertThat(service.atualizarMedicao(janelaId, end, end.plusSeconds(1))).isFalse();

        assertThat(timestamp("ultima_medicao_em", janelaId)).isEqualTo(start.plusSeconds(60));
    }

    @Test
    void claimConcorrenteNaoProcessaSimultaneamente() throws Exception {
        service.abrir(command("window-concurrent-claim", start, end));

        List<Object> results = runConcurrently(
                () -> service.claimAvailable(1, Duration.ofSeconds(30), 3),
                () -> service.claimAvailable(1, Duration.ofSeconds(30), 3)
        );

        long claimed = results.stream()
                .map(List.class::cast)
                .mapToLong(List::size)
                .sum();
        assertThat(claimed).isOne();
    }

    @Test
    void leaseExpiradoPermiteNovoClaimComAttemptIncrementado() {
        UUID janelaId = service.abrir(command("window-lease", start, end)).getId();
        assertThat(service.claimAvailable(1, Duration.ofSeconds(30), 3)).hasSize(1);

        jdbcTemplate.update("update evento_janela_avaliacao set lease_until = now() - interval '1 second' where id = ?", janelaId);
        var reclaimed = service.claimAvailable(1, Duration.ofSeconds(30), 3);

        assertThat(reclaimed).hasSize(1);
        assertThat(reclaimed.getFirst().attempts()).isEqualTo(2);
    }

    @Test
    void retryLiberaJanelaParaNovaAvaliacao() {
        UUID janelaId = service.abrir(command("window-retry", start, end)).getId();
        var work = service.claimAvailable(1, Duration.ofSeconds(30), 3).getFirst();

        assertThat(service.marcarRetry(work.janelaId(), start.plusSeconds(10), "temporary")).isTrue();

        assertThat(status(janelaId)).isEqualTo("ABERTA");
        assertThat(lastError(janelaId)).isEqualTo("temporary");
    }

    @Test
    void expiracaoPorFimPrevistoELacunaDeAmostragem() {
        UUID byEnd = service.abrir(command("window-expire-end", start, end)).getId();
        UUID byGap = service.abrir(new EventoJanelaAvaliacaoService.AbrirJanelaCommand(
                eventoId, compartimentoId, periodoAulaId, start, end.plusSeconds(3600),
                start, start.plusSeconds(60), "window-expire-gap", snapshot()
        )).getId();

        int expired = service.expirarJanelasVencidas(end.plusSeconds(600));

        assertThat(expired).isGreaterThanOrEqualTo(2);
        assertThat(status(byEnd)).isEqualTo("EXPIRADA");
        assertThat(status(byGap)).isEqualTo("EXPIRADA");
    }

    @Test
    void estadosFinaisSaoImutaveis() {
        UUID janelaId = service.abrir(command("window-final", start, end)).getId();

        assertThat(service.satisfazer(janelaId)).isTrue();
        assertThat(service.invalidar(janelaId, "late")).isFalse();
        assertThat(service.atualizarMedicao(janelaId, start.plusSeconds(30), start.plusSeconds(60))).isFalse();

        assertThat(status(janelaId)).isEqualTo("SATISFEITA");
    }

    @Test
    void estadoSobreviveReinicioSimuladoEAceitaEvidenciaIdempotente() {
        UUID janelaId = service.abrir(command("window-restart", start, end)).getId();

        var first = service.anexarEvidencia(new EventoJanelaAvaliacaoService.AnexarEvidenciaCommand(
                janelaId, medicaoId, parametroValorId, EventoJanelaEvidenciaPapel.INICIO
        ));
        var second = service.anexarEvidencia(new EventoJanelaAvaliacaoService.AnexarEvidenciaCommand(
                janelaId, medicaoId, parametroValorId, EventoJanelaEvidenciaPapel.INICIO
        ));

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(service.buscar(janelaId).getStatus().name()).isEqualTo("ABERTA");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from evento_janela_evidencia where evento_janela_avaliacao_id = ?",
                Integer.class,
                janelaId
        )).isEqualTo(1);
    }

    @Test
    void rollbackNaoDeixaJanelaParcial() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            service.abrir(command("window-rollback", start, end));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from evento_janela_avaliacao where chave_idempotencia = 'window-rollback'",
                Integer.class
        )).isZero();
    }

    @Test
    void migrationPossuiConstraintsEIndicesPrincipais() {
        assertThat(indexExists("ux_evento_janela_chave_idempotencia")).isTrue();
        assertThat(indexExists("ix_evento_janela_status_proxima")).isTrue();
        assertThat(indexExists("ix_evento_janela_lease")).isTrue();
        assertThat(indexExists("ux_evento_janela_evidencia_parametro_valor")).isTrue();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into evento_janela_avaliacao
                (evento_definicao_id, compartimento_id, status, inicio_em, fim_previsto_em,
                 proxima_avaliacao_em, chave_idempotencia, contexto_snapshot, created_at, updated_at)
                values (?, ?, 'ABERTA', now(), now(), now(), 'bad-window', '{}'::jsonb, now(), now())
                """, eventoId, compartimentoId)).isInstanceOf(RuntimeException.class);
    }

    private EventoJanelaAvaliacaoService.AbrirJanelaCommand command(String key, Instant inicio, Instant fim) {
        return new EventoJanelaAvaliacaoService.AbrirJanelaCommand(
                eventoId,
                compartimentoId,
                periodoAulaId,
                inicio,
                fim,
                inicio,
                next,
                key,
                snapshot()
        );
    }

    private String snapshot() {
        return """
                {"eventDefinitionId":"%s","compartimentoId":"%s","periodoAulaId":"%s"}
                """.formatted(eventoId, compartimentoId, periodoAulaId);
    }

    private String status(UUID janelaId) {
        return jdbcTemplate.queryForObject("select status from evento_janela_avaliacao where id = ?", String.class, janelaId);
    }

    private String lastError(UUID janelaId) {
        return jdbcTemplate.queryForObject("select last_error from evento_janela_avaliacao where id = ?", String.class, janelaId);
    }

    private Instant timestamp(String column, UUID janelaId) {
        Timestamp value = jdbcTemplate.queryForObject(
                "select " + column + " from evento_janela_avaliacao where id = ?",
                Timestamp.class,
                janelaId
        );
        return value == null ? null : value.toInstant();
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
            Callable<List<EventoJanelaAvaliacaoService.EventoJanelaWork>> first,
            Callable<List<EventoJanelaAvaliacaoService.EventoJanelaWork>> second
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
            Callable<List<EventoJanelaAvaliacaoService.EventoJanelaWork>> task,
            CountDownLatch ready,
            CountDownLatch startTogether
    ) {
        return () -> {
            ready.countDown();
            startTogether.await();
            return task.call();
        };
    }
}
