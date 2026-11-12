package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleRequest;
import com.proben.api.observability.ApiObservabilityMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "proben.sensors.seed-path=missing-test-sensors.json",
        "proben.security.bootstrap-admin.enabled=false",
        "proben.shared-context.collector.enabled=false",
        "proben.environment-rules.worker.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class EnvironmentRuleWorkerConcurrencyIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("PROBEN_CREDENTIAL_ENCRYPTION_KEY", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Autowired EnvironmentRuleService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApiObservabilityMetrics metrics;

    private String room;
    private String category;

    @BeforeEach
    void setup() {
        jdbc.execute("drop trigger if exists test_delay_env_completion on regra_ambiente_execucao");
        jdbc.execute("drop function if exists test_delay_env_completion()");
        jdbc.update("delete from pontos_lancamento where origem='ENVIRONMENT_RULE'");
        jdbc.update("delete from regra_ambiente_execucao");
        jdbc.update("delete from regra_ambiente");
        String suffix = UUID.randomUUID().toString();
        String campus = "WORK-C-" + suffix;
        String unit = "WORK-U-" + suffix;
        String building = "WORK-B-" + suffix;
        room = "WORK-R-" + suffix;
        category = "WORK_" + suffix.replace("-", "").toUpperCase();
        jdbc.update("insert into campus(nome) values (?)", campus);
        jdbc.update("insert into unidade(nome) values (?)", unit);
        jdbc.update("insert into predio(id,campus_id,nome) values (?,?,?)", building, campus, building);
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values (?,?,?,?,'Sala')", room, building, unit, room);
    }

    @Test
    void twoLogicalWorkersConcurrentlyClaimOnlyOnceUsingSkipLocked() throws Exception {
        var execution = service.manual(rule().id(), room, Instant.now().minusSeconds(1));
        EnvironmentRuleWorker first = worker(5);
        EnvironmentRuleWorker second = worker(5);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> { start.await(); return first.processAvailable(); });
            var right = pool.submit(() -> { start.await(); return second.processAvailable(); });
            start.countDown();
            assertThat(left.get(10, TimeUnit.SECONDS) + right.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        var completed = service.execution(execution.id());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.attempts()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from regra_ambiente_execucao where id=?", Integer.class, execution.id())).isEqualTo(1);
    }

    @Test
    void validLeaseCannotBeStolenByAnotherLogicalWorker() {
        var execution = service.manual(rule().id(), room, Instant.now().minusSeconds(1));
        UUID token = UUID.randomUUID();
        jdbc.update("update regra_ambiente_execucao set status='PROCESSING',attempts=1,lease_token=?,lease_until=now()+interval '1 minute' where id=?", token, execution.id());
        assertThat(worker(5).processAvailable()).isZero();
        assertThat(jdbc.queryForObject("select lease_token from regra_ambiente_execucao where id=?", UUID.class, execution.id())).isEqualTo(token);
    }

    @Test
    void expiredLeaseGetsANewTokenAndOldOwnerCannotFinalizeOrAudit() throws Exception {
        var execution = service.manual(rule().id(), room, Instant.now().minusSeconds(1));
        UUID oldToken = UUID.randomUUID();
        jdbc.update("update regra_ambiente_execucao set status='PROCESSING',attempts=1,lease_token=?,lease_until=now()-interval '1 second' where id=?", oldToken, execution.id());
        installCompletionDelay();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var processing = pool.submit(() -> worker(5).processAvailable());
            UUID newToken = awaitNewToken(execution.id(), oldToken);
            assertThat(newToken).isNotEqualTo(oldToken);
            assertThat(jdbc.update("update regra_ambiente_execucao set motivo='old owner audit' where id=? and lease_token=?", execution.id(), oldToken)).isZero();
            assertThat(processing.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(service.execution(execution.id()).status()).isEqualTo("COMPLETED");
        assertThat(service.execution(execution.id()).attempts()).isEqualTo(2);
    }

    @Test
    void concurrentMaterializationCreatesOneExecutionIdentity() throws Exception {
        var rule = rule();
        jdbc.update("update regra_ambiente set cron_expression='0 * * * * *' where id=?", rule.id());
        Instant due = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> { start.await(); return worker(5).materializeDue(due); });
            var right = pool.submit(() -> { start.await(); return worker(5).materializeDue(due); });
            start.countDown();
            left.get(10, TimeUnit.SECONDS);
            right.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from regra_ambiente_execucao where regra_id=? and compartimento_id=? and instante_previsto=?", Integer.class, rule.id(), room, Timestamp.from(due))).isEqualTo(1);
    }

    @Test
    void incompleteClaimIsRecoveredAfterRestartAndCompletedWorkIsNotRepeated() {
        var execution = service.manual(rule().id(), room, Instant.now().minusSeconds(1));
        jdbc.update("update regra_ambiente_execucao set status='PROCESSING',attempts=1,lease_token=gen_random_uuid(),lease_until=now()-interval '1 second' where id=?", execution.id());
        EnvironmentRuleWorker restarted = worker(5);
        assertThat(restarted.processAvailable()).isEqualTo(1);
        assertThat(service.execution(execution.id()).status()).isEqualTo("COMPLETED");
        assertThat(restarted.processAvailable()).isZero();
        assertThat(service.execution(execution.id()).attempts()).isEqualTo(2);
    }

    @Test
    void transientFailureSchedulesRetryAndLaterSuccessCompletesWithoutDuplication() {
        var rule = rule();
        var execution = service.manual(rule.id(), room, Instant.now().minusSeconds(1));
        String original = jdbc.queryForObject("select regra_snapshot::text from regra_ambiente_execucao where id=?", String.class, execution.id());
        jdbc.update("update regra_ambiente_execucao set regra_snapshot='{}'::jsonb where id=?", execution.id());
        EnvironmentRuleWorker worker = worker(3);
        assertThat(worker.processAvailable()).isEqualTo(1);
        var retry = service.execution(execution.id());
        assertThat(retry.status()).isEqualTo("RETRY");
        assertThat(retry.attempts()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select available_at>now() from regra_ambiente_execucao where id=?", Boolean.class, execution.id())).isTrue();
        assertThat(worker.processAvailable()).isZero();
        jdbc.update("update regra_ambiente_execucao set regra_snapshot=?::jsonb,available_at=now() where id=?", original, execution.id());
        assertThat(worker.processAvailable()).isEqualTo(1);
        assertThat(service.execution(execution.id()).status()).isEqualTo("COMPLETED");
        assertThat(service.execution(execution.id()).attempts()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from regra_ambiente_execucao where id=?", Integer.class, execution.id())).isEqualTo(1);
    }

    @Test
    void retryLimitProducesPermanentFailureWithoutStuckLeaseOrPoints() {
        var execution = service.manual(rule().id(), room, Instant.now().minusSeconds(1));
        jdbc.update("update regra_ambiente_execucao set regra_snapshot='{}'::jsonb where id=?", execution.id());
        EnvironmentRuleWorker worker = worker(2);
        assertThat(worker.processAvailable()).isEqualTo(1);
        jdbc.update("update regra_ambiente_execucao set available_at=now() where id=?", execution.id());
        assertThat(worker.processAvailable()).isEqualTo(1);
        var failed = service.execution(execution.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.resultado()).isEqualTo("FAILED");
        assertThat(failed.attempts()).isEqualTo(2);
        assertThat(failed.leaseUntil()).isNull();
        assertThat(jdbc.queryForObject("select lease_token is null from regra_ambiente_execucao where id=?", Boolean.class, execution.id())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=?", Integer.class, execution.id())).isZero();
    }

    @Test
    void conversionFailureIsTreatedAsMissingDataAndNeverAwardsPoints() {
        String suffix = UUID.randomUUID().toString();
        String type = "CONVERSION-" + suffix;
        String sensor = "CONVERSION-SENSOR-" + suffix;
        UUID parameter = UUID.randomUUID();
        UUID measurement = UUID.randomUUID();
        jdbc.update("insert into tipo_de_sensor(nome) values (?)", type);
        jdbc.update("insert into parametro_def(id,data_type,tipo_nome,nome,ativo) values (?,'TEXT',?,'estado',true)", parameter, type);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,compartimento_id,ativo) values (?,?,?,?,true)", sensor, sensor, type, room);
        jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,now(),now(),'TEST')", measurement, sensor);
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,text_value) values (?,?,?,'not-a-number')", UUID.randomUUID(), measurement, parameter);
        ObjectNode config = json.createObjectNode().put("combination", "AND");
        config.putArray("conditions").add(json.createObjectNode()
                .put("alias", "invalidText").put("parametroDefId", parameter.toString()).put("sensorExternalId", sensor)
                .put("source", "LOCAL").put("operator", "GTE").put("value", 1).put("maxAgeSeconds", 600));
        var invalidRule = service.create(new RuleRequest("Conversion failure", null, category, true, true, "GLOBAL", null, null,
                "America/Sao_Paulo", EnvironmentRuleService.TEN_MINUTES_PRESET, List.of(1, 2, 3, 4, 5, 6, 7),
                null, null, "IGNORE", "NONE", true,
                json.createObjectNode().put("satisfied", 50).put("notSatisfied", 25), config));
        var pending = service.manual(invalidRule.id(), room, Instant.now().minusSeconds(1));

        assertThat(worker(1).processAvailable()).isEqualTo(1);

        var execution = service.execution(pending.id());
        assertThat(execution.status()).isEqualTo("COMPLETED");
        assertThat(execution.resultado()).isEqualTo("SKIPPED_NO_DATA");
        assertThat(execution.pontosCalculados()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=?", Integer.class, pending.id())).isZero();
    }

    @Test
    void failingRuleDoesNotBlockIndependentExecution() {
        var firstRule = rule();
        var first = service.manual(firstRule.id(), room, Instant.now().minusSeconds(1));
        service.setActive(firstRule.id(), false);
        category = category + "X";
        var second = service.manual(rule().id(), room, Instant.now().minusSeconds(1));
        jdbc.update("update regra_ambiente_execucao set regra_snapshot='{}'::jsonb where id=?", first.id());
        assertThat(worker(3).processAvailable()).isEqualTo(2);
        assertThat(service.execution(first.id()).status()).isEqualTo("RETRY");
        assertThat(service.execution(second.id()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void canonicalPointsIdempotencyConstraintHoldsUnderConcurrentInserts() throws Exception {
        var execution = service.manual(rule().id(), room, Instant.now().minusSeconds(1));
        jdbc.update("insert into pessoa(id,email,nome,password,created_at) values ('concurrent-beneficiary',?,'Concurrent','test',now())", UUID.randomUUID() + "@test.local");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var insert = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try {
                    return jdbc.update("insert into pontos_lancamento(pessoa_id,tipo,pontos,chave_idempotencia,origem,regra_ambiente_execucao_id,compartimento_id) values ('concurrent-beneficiary','CONCESSAO',5,?,'ENVIRONMENT_RULE',?,?)", "pontos:ambiente:" + execution.id() + ":concurrent-beneficiary", execution.id(), room) == 1;
                } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
                    return false;
                }
            };
            var left = pool.submit(insert);
            var right = pool.submit(insert);
            start.countDown();
            assertThat(List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=? and pessoa_id='concurrent-beneficiary'", Integer.class, execution.id())).isEqualTo(1);
    }

    private EnvironmentRuleWorker worker(int attempts) {
        return new EnvironmentRuleWorker(service, jdbc, new TransactionTemplate(transactionManager), json, metrics,
                false, 20, attempts, Duration.ofSeconds(30));
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule() {
        return service.create(new RuleRequest("Worker rule", null, category, true, true, "GLOBAL", null, null,
                "America/Sao_Paulo", EnvironmentRuleService.TEN_MINUTES_PRESET, List.of(1, 2, 3, 4, 5, 6, 7),
                null, null, "IGNORE", "NONE", true,
                json.createObjectNode().put("satisfied", 0).put("notSatisfied", 0),
                json.createObjectNode().set("conditions", json.createArrayNode())));
    }

    private void installCompletionDelay() {
        jdbc.execute("create function test_delay_env_completion() returns trigger language plpgsql as $$ begin if new.status='COMPLETED' then perform pg_sleep(1); end if; return new; end $$");
        jdbc.execute("create trigger test_delay_env_completion before update on regra_ambiente_execucao for each row execute function test_delay_env_completion()");
    }

    private UUID awaitNewToken(UUID execution, UUID oldToken) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            var tokens = jdbc.query("select lease_token from regra_ambiente_execucao where id=? and status='PROCESSING'", (rs, row) -> rs.getObject(1, UUID.class), execution);
            if (!tokens.isEmpty() && tokens.getFirst() != null && !tokens.getFirst().equals(oldToken)) return tokens.getFirst();
            Thread.sleep(20);
        }
        throw new AssertionError("new lease token was not observable");
    }
}
