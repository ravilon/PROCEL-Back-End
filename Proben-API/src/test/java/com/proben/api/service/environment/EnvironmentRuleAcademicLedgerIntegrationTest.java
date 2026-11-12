package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleRequest;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.TestRequest;
import com.proben.api.exception.ConflictException;
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
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "proben.sensors.seed-path=missing-test-sensors.json",
        "proben.security.bootstrap-admin.enabled=false",
        "proben.shared-context.collector.enabled=false",
        "proben.environment-rules.worker.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class EnvironmentRuleAcademicLedgerIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    private static final ZoneId ACADEMIC_ZONE = ZoneId.of("America/Sao_Paulo");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("PROBEN_CREDENTIAL_ENCRYPTION_KEY", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Autowired EnvironmentRuleService service;
    @Autowired EnvironmentRuleWorker worker;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private String campus;
    private String room;
    private String otherRoom;
    private String category;
    private Instant at;
    private long discipline;

    @BeforeEach
    void setup() {
        jdbc.execute("drop trigger if exists test_reject_partial_environment_xp on pontos_lancamento");
        jdbc.execute("drop function if exists test_reject_partial_environment_xp()");
        jdbc.update("delete from pontos_lancamento where origem='ENVIRONMENT_RULE'");
        jdbc.update("delete from regra_ambiente_execucao");
        jdbc.update("delete from regra_ambiente");
        String suffix = UUID.randomUUID().toString();
        campus = "ACA-C-" + suffix;
        String unit = "ACA-U-" + suffix;
        String building = "ACA-B-" + suffix;
        room = "ACA-R-" + suffix;
        otherRoom = "ACA-OTHER-" + suffix;
        category = "ACA_" + suffix.replace("-", "").toUpperCase();
        discipline = Math.abs(UUID.randomUUID().getMostSignificantBits());
        at = LocalDate.now(ACADEMIC_ZONE).minusDays(1).atTime(12, 0).atZone(ACADEMIC_ZONE).toInstant();
        jdbc.update("insert into campus(nome) values (?)", campus);
        jdbc.update("insert into unidade(nome) values (?)", unit);
        jdbc.update("insert into predio(id,campus_id,nome) values (?,?,?)", building, campus, building);
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values (?,?,?,?,'Sala'),(?,?,?,?,'Sala')",
                room, building, unit, room, otherRoom, building, unit, otherRoom);
        jdbc.update("insert into disciplina(id,nome) values (?,?)", discipline, "Discipline " + suffix);
    }

    @Test
    void roomWithoutScheduledUseOrOccupancyIsIgnoredWhenOccupiedIsRequired() {
        var result = dryRun(rule("REQUIRE_OCCUPIED", "NONE", false, 5));
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_OCCUPANCY");
        assertThat(result.ocupacao().path("occupied").asBoolean()).isFalse();
    }

    @Test
    void roomWithoutScheduleButWithSensorOccupancyIsEvaluated() {
        UUID occupancy = occupancyMeasurement(true);
        var result = dryRun(ruleWithOccupancy("REQUIRE_OCCUPIED", "NONE", false, 5, occupancy));
        assertThat(result.resultado()).isEqualTo("SATISFIED");
        assertThat(result.ocupacao().path("source").asText()).isEqualTo("SENSOR");
    }

    @Test
    void activeScheduledPeriodSuppliesAcademicContextAndOccupancy() {
        period(room, at.minusSeconds(600), at.plusSeconds(600), "T1");
        var result = dryRun(rule("REQUIRE_SCHEDULED_USE", "NONE", false, 5));
        assertThat(result.resultado()).isEqualTo("SATISFIED");
        assertThat(result.contextoAcademico().path("disciplinaId").asLong()).isEqualTo(discipline);
        assertThat(result.ocupacao().path("source").asText()).isEqualTo("SCHEDULED_USE");
    }

    @Test
    void endedOrNotStartedPeriodDoesNotCountAsScheduledUse() {
        period(room, at.minusSeconds(1200), at.minusSeconds(600), "ENDED");
        period(room, at.plusSeconds(600), at.plusSeconds(1200), "FUTURE");
        var result = dryRun(rule("REQUIRE_SCHEDULED_USE", "NONE", false, 5));
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_SCHEDULED_USE");
    }

    @Test
    void conflictingActivePeriodsAreRejectedAsAmbiguous() {
        period(room, at.minusSeconds(600), at.plusSeconds(600), "T1");
        period(room, at.minusSeconds(300), at.plusSeconds(300), "T2");
        var rule = rule("IGNORE", "NONE", false, 5);
        assertThatThrownBy(() -> dryRun(rule)).isInstanceOf(ConflictException.class).hasMessageContaining("Ambiguous academic context");
    }

    @Test
    void academicLinkDoesNotImplyPresenceWhenConfirmationIsRequired() {
        period(room, at.minusSeconds(600), at.plusSeconds(600), "T1");
        student("student-no-presence", "T1", "2026-2");
        var result = dryRun(rule("IGNORE", "CONFIRMED_PRESENCE", false, 5));
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_BENEFICIARY");
        assertThat(result.pontosCalculados()).isZero();
    }

    @Test
    void confirmedOpenCheckinReceivesCanonicalPointsLaunch() {
        period(room, at.minusSeconds(600), at.plusSeconds(600), "T1");
        student("student-present", "T1", "2026-2");
        presence("student-present", room, at.minusSeconds(60), null);
        var execution = execute(rule("IGNORE", "CONFIRMED_PRESENCE", false, 7));
        assertThat(execution.resultado()).isEqualTo("SATISFIED");
        assertThat(execution.beneficiarios()).hasSize(1);
        assertThat(execution.beneficiarios().get(0).asText()).isEqualTo("student-present");
        assertThat(execution.lancamentos()).hasSize(1);
        var ledger = jdbc.queryForMap("select * from pontos_lancamento where regra_ambiente_execucao_id=?", execution.id());
        assertThat(ledger.get("pessoa_id")).isEqualTo("student-present");
        assertThat(ledger.get("pontos")).isEqualTo(7);
        assertThat(ledger.get("origem")).isEqualTo("ENVIRONMENT_RULE");
        assertThat(ledger.get("compartimento_id")).isEqualTo(room);
        assertThat(ledger.get("created_at")).isNotNull();
    }

    @Test
    void presenceFromAnotherRoomOrOutsideExecutionInstantNeverAwardsPoints() {
        period(room, at.minusSeconds(600), at.plusSeconds(600), "T1");
        student("wrong-presence", "T1", "2026-2");
        presence("wrong-presence", otherRoom, at.minusSeconds(60), null);
        presence("wrong-presence", room, at.minusSeconds(600), at.minusSeconds(300));
        var execution = execute(rule("IGNORE", "CONFIRMED_PRESENCE", false, 7));
        assertThat(execution.resultado()).isEqualTo("SKIPPED_NO_BENEFICIARY");
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=?", Integer.class, execution.id())).isZero();
    }

    @Test
    void scheduledStudentPolicyAwardsLinkedStudentsWithoutCallingThemPresent() {
        period(room, at.minusSeconds(600), at.plusSeconds(600), "T1");
        student("linked-student", "T1", "2026-2");
        var execution = execute(rule("IGNORE", "SCHEDULED_STUDENTS", false, 6));
        assertThat(execution.resultado()).isEqualTo("SATISFIED");
        assertThat(execution.beneficiarios()).hasSize(1);
        assertThat(execution.beneficiarios().get(0).asText()).isEqualTo("linked-student");
        assertThat(execution.contextoAcademico().path("pessoaElegivelIds")).hasSize(1);
        assertThat(execution.contextoAcademico().path("pessoaElegivelIds").get(0).asText()).isEqualTo("linked-student");
        assertThat(jdbc.queryForObject("select count(*) from presenca where pessoa_id='linked-student'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=?", Integer.class, execution.id())).isEqualTo(1);
    }

    @Test
    void monitoringWithoutBeneficiariesPersistsExecutionWithoutPoints() {
        var execution = execute(rule("IGNORE", "NONE", true, 10));
        assertThat(execution.status()).isEqualTo("COMPLETED");
        assertThat(execution.resultado()).isEqualTo("SATISFIED");
        assertThat(execution.pontosCalculados()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=?", Integer.class, execution.id())).isZero();
    }

    @Test
    void severalConfirmedBeneficiariesGetOneIdempotentLaunchEach() {
        period(room, at.minusSeconds(600), at.plusSeconds(600), "T1");
        for (String person : List.of("student-a", "student-b")) {
            student(person, "T1", "2026-2");
            presence(person, room, at.minusSeconds(60), null);
        }
        var execution = execute(rule("IGNORE", "CONFIRMED_PRESENCE", false, 8));
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=?", Integer.class, execution.id())).isEqualTo(2);
        assertThat(worker.processAvailable()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=?", Integer.class, execution.id())).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("insert into pontos_lancamento(pessoa_id,tipo,pontos,chave_idempotencia,origem,regra_ambiente_execucao_id,compartimento_id) values ('student-a','CONCESSAO',8,?,'ENVIRONMENT_RULE',?,?)", "duplicate-key", execution.id(), room))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void partialLedgerFailureRollsBackEveryLaunchAndLeavesExecutionRetryable() {
        period(room, at.minusSeconds(600), at.plusSeconds(600), "T1");
        for (String person : List.of("rollback-a", "rollback-b")) {
            student(person, "T1", "2026-2");
            presence(person, room, at.minusSeconds(60), null);
        }
        jdbc.execute("create function test_reject_partial_environment_xp() returns trigger language plpgsql as $$ begin if new.pessoa_id='rollback-b' then raise exception 'injected ledger failure'; end if; return new; end $$");
        jdbc.execute("create trigger test_reject_partial_environment_xp before insert on pontos_lancamento for each row execute function test_reject_partial_environment_xp()");

        var rule = rule("IGNORE", "CONFIRMED_PRESENCE", false, 8);
        var pending = service.manual(rule.id(), room, at);
        assertThat(worker.processAvailable()).isEqualTo(1);

        var execution = service.execution(pending.id());
        assertThat(execution.status()).isEqualTo("RETRY");
        assertThat(execution.attempts()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pontos_lancamento where regra_ambiente_execucao_id=?", Integer.class, pending.id())).isZero();
    }

    @Test
    void manualAndScheduledExecutionsHaveDistinctIdentitiesAndNoParallelBalanceTable() {
        var rule = rule("IGNORE", "NONE", true, 5);
        var manual = service.manual(rule.id(), room, at);
        UUID scheduled = service.enqueue(rule, room, "GLOBAL_DEFAULT", "SCHEDULED", at, "SCHEDULED", "test");
        assertThat(manual.id()).isNotEqualTo(scheduled);
        assertThat(jdbc.queryForObject("select count(distinct idempotency_key) from regra_ambiente_execucao where id in (?,?)", Integer.class, manual.id(), scheduled)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name like '%saldo%'", Integer.class)).isZero();
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.TestResponse dryRun(
            com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule) {
        return service.test(rule.id(), new TestRequest(room, at));
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.ExecutionResponse execute(
            com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule) {
        var pending = service.manual(rule.id(), room, at);
        assertThat(worker.processAvailable()).isEqualTo(1);
        return service.execution(pending.id());
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule(
            String occupancy, String beneficiary, boolean monitoring, int points) {
        return service.create(request(occupancy, beneficiary, monitoring, points, json.createObjectNode().set("conditions", json.createArrayNode())));
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse ruleWithOccupancy(
            String occupancy, String beneficiary, boolean monitoring, int points, UUID occupancyParameter) {
        ObjectNode config = json.createObjectNode().put("occupancyParametroDefId", occupancyParameter.toString()).put("occupancyMaxAgeSeconds", 600);
        config.set("conditions", json.createArrayNode());
        return service.create(request(occupancy, beneficiary, monitoring, points, config));
    }

    private RuleRequest request(String occupancy, String beneficiary, boolean monitoring, int points, ObjectNode config) {
        return new RuleRequest("Academic rule", null, category, true, true, "GLOBAL", null, null,
                "America/Sao_Paulo", EnvironmentRuleService.TEN_MINUTES_PRESET, List.of(1, 2, 3, 4, 5, 6, 7),
                null, null, occupancy, beneficiary, monitoring,
                json.createObjectNode().put("satisfied", points).put("notSatisfied", 0), config);
    }

    private UUID occupancyMeasurement(boolean occupied) {
        String type = "OCC-" + UUID.randomUUID();
        String sensor = "OCC-S-" + UUID.randomUUID();
        UUID parameter = UUID.randomUUID();
        UUID measurement = UUID.randomUUID();
        jdbc.update("insert into tipo_de_sensor(nome) values (?)", type);
        jdbc.update("insert into parametro_def(id,data_type,tipo_nome,nome,ativo) values (?,'BOOLEAN',?,'presence',true)", parameter, type);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,compartimento_id,ativo) values (?,?,?,?,true)", sensor, sensor, type, room);
        jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,?,?,?)", measurement, sensor, Timestamp.from(at.minusSeconds(1)), Timestamp.from(at), "TEST");
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,boolean_value) values (?,?,?,?)", UUID.randomUUID(), measurement, parameter, occupied);
        return parameter;
    }

    private void period(String targetRoom, Instant start, Instant end, String group) {
        ZonedDateTime localStart = start.atZone(ACADEMIC_ZONE);
        ZonedDateTime localEnd = end.atZone(ACADEMIC_ZONE);
        jdbc.update("insert into periodo_aula(data,turno,periodo_aula,hora_inicio,hora_fim,sincronizado_em,disciplina_id,id,compartimento_id,tipo,turma,descricao) values (?,?,?,?,?,now(),?, ?,?,'AULA',?,?)",
                localStart.toLocalDate(), 1, Math.abs(group.hashCode() % 1000) + 1, localStart.toLocalTime(), localEnd.toLocalTime(), discipline,
                UUID.randomUUID(), targetRoom, group, group);
    }

    private void student(String id, String group, String academicPeriod) {
        jdbc.update("insert into pessoa(id,email,nome,password,created_at) values (?,?,?,?,now())", id, id + "@test.local", id, "test");
        jdbc.update("insert into aluno_disciplina(id,pessoa_id,disciplina_id,turma,periodo_letivo,status,vinculado_em) values (?,?,?,?,?,'ATIVA',now())",
                UUID.randomUUID(), id, discipline, group, academicPeriod);
    }

    private void presence(String person, String targetRoom, Instant checkin, Instant checkout) {
        jdbc.update("insert into presenca(id,pessoa_id,compartimento_id,checkin_at,checkout_at,created_at,source) values (?,?,?,?,?,now(),'TEST')",
                UUID.randomUUID(), person, targetRoom, Timestamp.from(checkin), checkout == null ? null : Timestamp.from(checkout));
    }
}
