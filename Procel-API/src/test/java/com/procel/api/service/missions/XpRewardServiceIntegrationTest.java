package com.procel.api.service.missions;

import com.procel.api.entity.missions.AtividadeStatus;
import com.procel.api.entity.missions.XpLancamento;
import com.procel.api.entity.missions.XpLancamentoTipo;
import com.procel.api.exception.ConflictException;
import com.procel.api.repository.missions.XpLancamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class XpRewardServiceIntegrationTest {

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
    @Autowired XpRewardService rewardService;
    @Autowired XpQueryService queryService;
    @Autowired XpLancamentoRepository lancamentoRepository;

    String pessoaId;
    UUID missaoId;
    UUID atividadeId;
    UUID eventoOcorrenciaId;
    Instant now;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        String suffix = UUID.randomUUID().toString();
        pessoaId = "xp-" + suffix;
        missaoId = UUID.randomUUID();
        atividadeId = UUID.randomUUID();
        eventoOcorrenciaId = UUID.randomUUID();
        now = Instant.parse("2026-09-13T10:00:00Z");

        seedPessoa(pessoaId);
        seedMissao(missaoId, 25, 1, true);
        seedOccurrence(eventoOcorrenciaId, missaoId, suffix);
        seedActivity(atividadeId, pessoaId, missaoId, AtividadeStatus.CONCLUIDA, "UNICA");
    }

    @Test
    void automaticCompletionGrantsMissionValueAndDuplicateReturnsExisting() {
        var first = rewardService.grantAutomaticCompletionReward(atividadeId, eventoOcorrenciaId, now);
        var duplicate = rewardService.grantAutomaticCompletionReward(atividadeId, eventoOcorrenciaId, now.plusSeconds(5));

        assertThat(first.created()).isTrue();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.lancamento().getId()).isEqualTo(first.lancamento().getId());
        assertThat(count("xp_lancamento")).isEqualTo(1);
        assertThat(sumQuantidade(pessoaId)).isEqualTo(25);
    }

    @Test
    void zeroValueSkipsLedgerCreation() {
        UUID zeroMission = UUID.randomUUID();
        UUID zeroActivity = UUID.randomUUID();
        seedMissao(zeroMission, 0, 1, true);
        seedActivity(zeroActivity, pessoaId, zeroMission, AtividadeStatus.CONCLUIDA, "ZERO");

        var result = rewardService.grantAutomaticCompletionReward(zeroActivity, eventoOcorrenciaId, now);

        assertThat(result.skippedZeroValue()).isTrue();
        assertThat(count("xp_lancamento")).isZero();
    }

    @Test
    void sameActivityAndSameOccurrenceDoNotDuplicateXpAndDifferentCycleGrantsSeparately() {
        UUID secondActivity = UUID.randomUUID();
        seedActivity(secondActivity, pessoaId, missaoId, AtividadeStatus.CONCLUIDA, "DIA:2026-09-14");

        rewardService.grantAutomaticCompletionReward(atividadeId, eventoOcorrenciaId, now);
        rewardService.grantAutomaticCompletionReward(atividadeId, eventoOcorrenciaId, now.plusSeconds(1));
        rewardService.grantAutomaticCompletionReward(secondActivity, eventoOcorrenciaId, now.plusSeconds(2));

        assertThat(count("xp_lancamento")).isEqualTo(2);
        assertThat(sumQuantidade(pessoaId)).isEqualTo(50);
    }

    @Test
    void concurrentAttemptCreatesSingleLedgerEntry() throws Exception {
        List<Object> results = runConcurrently(
                () -> rewardService.grantAutomaticCompletionReward(atividadeId, eventoOcorrenciaId, now),
                () -> rewardService.grantAutomaticCompletionReward(atividadeId, eventoOcorrenciaId, now)
        );

        assertThat(results).allSatisfy(result ->
                assertThat(result).isInstanceOf(XpRewardService.GrantResult.class));
        assertThat(count("xp_lancamento")).isEqualTo(1);
        assertThat(sumQuantidade(pessoaId)).isEqualTo(25);
    }

    @Test
    void divergentIdempotencyContentConflicts() {
        insertManualLedger(XpRewardService.automaticCompletionKey(atividadeId), 99, eventoOcorrenciaId);

        assertThatThrownBy(() -> rewardService.grantAutomaticCompletionReward(atividadeId, eventoOcorrenciaId, now))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different reward content");
    }

    @Test
    void notCompletedActivityIsRejected() {
        UUID pending = UUID.randomUUID();
        seedActivity(pending, pessoaId, missaoId, AtividadeStatus.EM_ANDAMENTO, "PENDING");

        assertThatThrownBy(() -> rewardService.grantAutomaticCompletionReward(pending, eventoOcorrenciaId, now))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("completed activity");
    }

    @Test
    void databaseRejectsInvalidSignsAndZero() {
        assertThatThrownBy(() -> insertLedger(UUID.randomUUID(), XpLancamentoTipo.CONCESSAO, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertLedger(UUID.randomUUID(), XpLancamentoTipo.ESTORNO, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertLedger(UUID.randomUUID(), XpLancamentoTipo.AJUSTE, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void entityRejectsInvalidSignsAndRepositoryDoesNotExposeDeleteMethods() {
        assertThatThrownBy(() -> new XpLancamento(null, null, null, null, XpLancamentoTipo.CONCESSAO,
                -1, "bad", null, now, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new XpLancamento(null, null, null, null, XpLancamentoTipo.ESTORNO,
                1, "bad", null, now, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new XpLancamento(null, null, null, null, XpLancamentoTipo.AJUSTE,
                0, "bad", null, now, null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(Arrays.stream(lancamentoRepository.getClass().getInterfaces())
                .flatMap(item -> Arrays.stream(item.getMethods()))
                .toList())
                .noneMatch(method -> method.getName().startsWith("delete"));
    }

    @Test
    void saldoSumsGrantsReversalsAndAdjustmentsAndStatementFiltersType() {
        insertLedger(UUID.randomUUID(), XpLancamentoTipo.CONCESSAO, 25);
        insertLedger(UUID.randomUUID(), XpLancamentoTipo.ESTORNO, -5);
        insertLedger(UUID.randomUUID(), XpLancamentoTipo.AJUSTE, 3);

        var saldo = queryService.saldo(pessoaId);
        var concessoes = queryService.lancamentos(pessoaId, XpLancamentoTipo.CONCESSAO, 0, 10);

        assertThat(saldo.saldoTotal()).isEqualTo(23);
        assertThat(saldo.totalConcedido()).isEqualTo(25);
        assertThat(saldo.totalEstornado()).isEqualTo(-5);
        assertThat(saldo.totalAjustado()).isEqualTo(3);
        assertThat(concessoes.content()).hasSize(1);
        assertThat(concessoes.content().getFirst().tipo()).isEqualTo(XpLancamentoTipo.CONCESSAO);
    }

    @Test
    void statementIsPagedAndOrderedByCreatedAtDescAndIdDesc() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        insertLedger(first, XpLancamentoTipo.AJUSTE, 1, now);
        insertLedger(second, XpLancamentoTipo.AJUSTE, 2, now);

        var page = queryService.lancamentos(pessoaId, null, 0, 1);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().id()).isEqualTo(second);
    }

    private void seedPessoa(String id) {
        jdbcTemplate.update("""
                insert into pessoa (id, nome, email, password, created_at)
                values (?, ?, ?, 'hash', now())
                """, id, id, id + "@example.com");
    }

    private void seedMissao(UUID id, int value, int progressoNecessario, boolean conclusaoAutomatica) {
        jdbcTemplate.update("""
                insert into missao
                (id, titulo, descricao, tipo, value, ativo, created_at, ciclo_tipo, progresso_necessario, conclusao_automatica)
                values (?, ?, 'Descricao', 'Individual', ?, true, now(), 'UNICA', ?, ?)
                """, id, "Missao " + id, value, progressoNecessario, conclusaoAutomatica);
    }

    private void seedActivity(UUID id, String pessoa, UUID missao, AtividadeStatus status, String chaveCiclo) {
        jdbcTemplate.update("""
                insert into atividade
                (id, pessoa_id, missao_id, status, assigned_at, started_at, completed_at, chave_ciclo, ciclo_tipo,
                 progresso_atual, progresso_necessario, conclusao_automatica)
                values (?, ?, ?, ?, now(), ?, ?, ?, 'UNICA', 1, 1, true)
                """,
                id,
                pessoa,
                missao,
                status.name(),
                Timestamp.from(now.minusSeconds(60)),
                status == AtividadeStatus.CONCLUIDA ? Timestamp.from(now) : null,
                chaveCiclo);
    }

    private void seedOccurrence(UUID occurrenceId, UUID mission, String suffix) {
        String roomId = "ROOM-" + suffix;
        UUID predioId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("insert into campus (nome) values (?)", "Campus-" + suffix);
        jdbcTemplate.update("insert into unidade (nome) values (?)", "Unidade-" + suffix);
        jdbcTemplate.update("insert into predio (id, campus_id, nome) values (?, ?, ?)", predioId, "Campus-" + suffix, "Predio");
        jdbcTemplate.update("""
                insert into compartimento (id, predio_id, unidade_id, nome, tipo)
                values (?, ?, ?, 'Sala', 'Sala')
                """, roomId, predioId, "Unidade-" + suffix);
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'Evento', 'MEDICAO_RECEBIDA', 'INSTANTANEO', 'ALL',
                        'SEM_ATRIBUICAO_AUTOMATICA', 1, 1, true, now(), now())
                """, eventId, mission);
        jdbcTemplate.update("""
                insert into evento_ocorrencia
                (id, evento_definicao_id, compartimento_id, status, inicio_em, fim_em,
                 detectado_em, chave_idempotencia, contexto_snapshot, conteudo_fingerprint, created_at, updated_at)
                values (?, ?, ?, 'CONFIRMADO', ?, ?, ?, ?, '{}'::jsonb, repeat('a', 64), now(), now())
                """,
                occurrenceId,
                eventId,
                roomId,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                "occurrence-" + suffix);
    }

    private void insertManualLedger(String key, int quantidade, UUID occurrence) {
        jdbcTemplate.update("""
                insert into xp_lancamento
                (id, pessoa_id, atividade_id, missao_id, evento_ocorrencia_id, tipo, quantidade,
                 chave_idempotencia, descricao, created_at, created_by)
                values (?, ?, ?, ?, ?, 'CONCESSAO', ?, ?, 'Manual conflict', ?, ?)
                """,
                UUID.randomUUID(),
                pessoaId,
                atividadeId,
                missaoId,
                occurrence,
                quantidade,
                key,
                Timestamp.from(now),
                XpLancamento.AUTO_COMPLETION_CREATED_BY);
    }

    private void insertLedger(UUID id, XpLancamentoTipo tipo, int quantidade) {
        insertLedger(id, tipo, quantidade, now.plusMillis(Math.abs(id.getLeastSignificantBits() % 1000)));
    }

    private void insertLedger(UUID id, XpLancamentoTipo tipo, int quantidade, Instant createdAt) {
        jdbcTemplate.update("""
                insert into xp_lancamento
                (id, pessoa_id, atividade_id, missao_id, tipo, quantidade, chave_idempotencia, descricao, created_at, created_by)
                values (?, ?, ?, ?, ?, ?, ?, 'Test ledger', ?, 'TEST')
                """,
                id,
                pessoaId,
                atividadeId,
                missaoId,
                tipo.name(),
                quantidade,
                "test:" + id,
                Timestamp.from(createdAt));
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

    private long count(String table) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }

    private long sumQuantidade(String pessoa) {
        Long total = jdbcTemplate.queryForObject(
                "select coalesce(sum(quantidade), 0) from xp_lancamento where pessoa_id = ?",
                Long.class,
                pessoa
        );
        return total == null ? 0 : total;
    }

    private List<Object> runConcurrently(
            Callable<XpRewardService.GrantResult> first,
            Callable<XpRewardService.GrantResult> second
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
            Callable<XpRewardService.GrantResult> task,
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
