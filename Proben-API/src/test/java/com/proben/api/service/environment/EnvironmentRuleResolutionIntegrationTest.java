package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleRequest;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.CopyRequest;
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

import java.util.List;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
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
class EnvironmentRuleResolutionIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("PROBEN_CREDENTIAL_ENCRYPTION_KEY", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Autowired EnvironmentRuleService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EnvironmentRuleWorker worker;

    @BeforeEach void setup() {
        jdbc.update("delete from regra_ambiente_execucao");
        jdbc.update("delete from regra_ambiente");
        jdbc.update("delete from compartimento where id in ('ENV-R1','ENV-R2')");
        jdbc.update("delete from compartimento where id='ENV-R3'");
        jdbc.update("delete from predio where id in ('ENV-B','ENV-B2')");
        jdbc.update("delete from unidade where nome='ENV-U'");
        jdbc.update("delete from campus where nome in ('ENV-C','ENV-C2')");
        jdbc.update("insert into campus(nome) values ('ENV-C'),('ENV-C2')");
        jdbc.update("insert into unidade(nome) values ('ENV-U')");
        jdbc.update("insert into predio(id,campus_id,nome) values ('ENV-B','ENV-C','ENV-B'),('ENV-B2','ENV-C2','ENV-B2')");
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values ('ENV-R1','ENV-B','ENV-U','Room 1','Sala'),('ENV-R2','ENV-B','ENV-U','Room 2','Sala')");
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values ('ENV-R3','ENV-B2','ENV-U','Room 3','Sala')");
    }

    @Test void resolvesSpecificThenCampusThenGlobalAndFallsBackAfterDeactivation() {
        var global = service.create(request("Global", "GLOBAL", null, null, true));
        var campus = service.create(request("Campus", "CAMPUS", "ENV-C", null, true));
        var room = service.create(request("Room", "ROOM", null, "ENV-R1", false));
        assertThat(service.resolve("ENV-R1", "ILUMINACAO").effective().id()).isEqualTo(room.id());
        assertThat(service.resolve("ENV-R2", "ILUMINACAO").effective().id()).isEqualTo(campus.id());
        service.setActive(room.id(), false);
        assertThat(service.resolve("ENV-R1", "ILUMINACAO").effective().id()).isEqualTo(campus.id());
        service.setActive(campus.id(), false);
        assertThat(service.resolve("ENV-R1", "ILUMINACAO").effective().id()).isEqualTo(global.id());
    }

    @Test void rejectsSecondActiveDefaultAndBlocksDeletionWithHistory() {
        var global = service.create(request("Global", "GLOBAL", null, null, true));
        assertThatThrownBy(() -> service.create(request("Other", "GLOBAL", null, null, true)))
                .isInstanceOf(ConflictException.class);
        service.manual(global.id(), "ENV-R1", null);
        assertThat(service.deletePreview(global.id()).deletable()).isFalse();
        assertThatThrownBy(() -> service.delete(global.id())).isInstanceOf(ConflictException.class);
    }

    @Test void scheduledMaterializationIsIdempotentAndDoesNotRunSpecificAndFallbackTogether() {
        service.create(request("Global", "GLOBAL", null, null, true));
        service.create(request("Campus", "CAMPUS", "ENV-C", null, true));
        service.create(request("Room", "ROOM", null, "ENV-R1", false));
        jdbc.update("update regra_ambiente set cron_expression='0 * * * * *'");
        Instant at = Instant.now();
        worker.materializeDue(at);
        Integer firstTotal = jdbc.queryForObject("select count(*) from regra_ambiente_execucao", Integer.class);
        worker.materializeDue(at);
        Integer total = jdbc.queryForObject("select count(*) from regra_ambiente_execucao", Integer.class);
        Integer doubleRuns = jdbc.queryForObject("select count(*) from (select compartimento_id,categoria,instante_previsto,count(*) n from regra_ambiente_execucao group by 1,2,3 having count(*)>1) duplicated", Integer.class);
        assertThat(firstTotal).isPositive();
        assertThat(total).isEqualTo(firstTotal);
        assertThat(doubleRuns).isZero();
        assertThat(worker.processAvailable()).isEqualTo(total);
        assertThat(jdbc.queryForObject("select count(*) from regra_ambiente_execucao where status='COMPLETED' and resultado='SATISFIED'", Integer.class)).isEqualTo(total);
    }

    @Test void expiredLeaseIsRecoveredAndOldOwnerCannotFinish() {
        var global = service.create(request("Global", "GLOBAL", null, null, true));
        var execution = service.manual(global.id(), "ENV-R1", Instant.now().minus(1, ChronoUnit.MINUTES));
        UUID oldToken = UUID.randomUUID();
        jdbc.update("update regra_ambiente_execucao set status='PROCESSING',attempts=1,lease_token=?,lease_until=now()-interval '1 second' where id=?", oldToken, UUID.fromString(execution.id().toString()));
        assertThat(worker.processAvailable()).isEqualTo(1);
        assertThat(service.execution(UUID.fromString(execution.id().toString())).status()).isEqualTo("COMPLETED");
        assertThat(jdbc.update("update regra_ambiente_execucao set motivo='stale owner' where id=? and lease_token=?", UUID.fromString(execution.id().toString()), oldToken)).isZero();
    }

    @Test void roomsFromDifferentCampusesResolveTheirOwnDefaults() {
        var first = service.create(request("Campus C", "CAMPUS", "ENV-C", null, true));
        var second = service.create(request("Campus C2", "CAMPUS", "ENV-C2", null, true));
        assertThat(service.resolve("ENV-R1", "ILUMINACAO").effective().id()).isEqualTo(first.id());
        assertThat(service.resolve("ENV-R3", "ILUMINACAO").effective().id()).isEqualTo(second.id());
    }

    @Test void scopeConstraintsAreRejectedBeforePersistence() {
        assertThatThrownBy(() -> service.create(request("Room missing", "ROOM", null, null, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ROOM");
        assertThatThrownBy(() -> service.create(request("Campus missing", "CAMPUS", null, null, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CAMPUS");
        assertThatThrownBy(() -> service.create(request("Campus with room", "CAMPUS", "ENV-C", "ENV-R1", true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CAMPUS");
        assertThatThrownBy(() -> service.create(request("Global with campus", "GLOBAL", "ENV-C", null, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("GLOBAL");
    }

    @Test void invalidCronAndTimezoneAreRejected() {
        assertThatThrownBy(() -> service.create(withSchedule(request("Bad cron", "GLOBAL", null, null, true), "not-a-cron", "UTC")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(withSchedule(request("Bad zone", "GLOBAL", null, null, true), EnvironmentRuleService.TEN_MINUTES_PRESET, "Mars/Olympus")))
                .isInstanceOf(DateTimeException.class);
    }

    @Test void declarativeConfigurationRejectsUnknownOperatorsAndDoesNotInterpretCode() {
        var invalid = json.createObjectNode();
        invalid.putArray("conditions").addObject()
                .put("alias", "unsafe").put("parametroDefId", UUID.randomUUID().toString())
                .put("source", "LOCAL").put("operator", "EVAL").put("value", "System.exit(1)");
        assertThatThrownBy(() -> service.create(withConfiguration(request("Invalid operator", "GLOBAL", null, null, true), invalid)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("operator");

        var inert = json.createObjectNode();
        inert.put("script", "java.lang.Runtime.getRuntime().exec('anything')");
        inert.putArray("conditions");
        var stored = service.create(withConfiguration(request("Inert data", "GLOBAL", null, null, true), inert));
        assertThat(stored.configuracao().path("script").asText()).contains("Runtime");
        assertThat(jdbc.queryForObject("select count(*) from regra_ambiente", Integer.class)).isEqualTo(1);
    }

    @Test void updateKeepsTheExecutionSnapshotAndDeletionWithoutHistoryWorks() {
        var rule = service.create(request("Original", "GLOBAL", null, null, true));
        var execution = service.manual(rule.id(), "ENV-R1", Instant.now());
        var updated = service.update(rule.id(), request("Edited", "GLOBAL", null, null, true));
        assertThat(updated.versao()).isEqualTo(rule.versao() + 1);
        assertThat(jdbc.queryForObject("select regra_snapshot->>'nome' from regra_ambiente_execucao where id=?", String.class, execution.id()))
                .isEqualTo("Original");
        assertThat(service.execution(execution.id()).regraVersao()).isEqualTo(rule.versao());

        var disposable = service.create(request("Disposable", "ROOM", null, "ENV-R2", false));
        service.delete(disposable.id());
        assertThat(jdbc.queryForObject("select count(*) from regra_ambiente where id=?", Integer.class, disposable.id())).isZero();
    }

    @Test void validLeasePreventsASecondClaimAndManualExecutionIsDistinguished() {
        var global = service.create(request("Global", "GLOBAL", null, null, true));
        var execution = service.manual(global.id(), "ENV-R1", Instant.now().minusSeconds(1));
        jdbc.update("update regra_ambiente_execucao set status='PROCESSING',attempts=1,lease_token=gen_random_uuid(),lease_until=now()+interval '1 minute' where id=?", execution.id());
        assertThat(worker.processAvailable()).isZero();
        assertThat(service.execution(execution.id()).tipoExecucao()).isEqualTo("MANUAL");
    }

    @Test void copyCreatesIndependentRoomRulesAndRollsBackAtomicallyOnFailure() {
        var original = service.create(request("Original", "ROOM", null, "ENV-R1", false));
        var copies = service.copyToRooms(original.id(), new CopyRequest(List.of("ENV-R2", "ENV-R3")));
        assertThat(copies).hasSize(2).extracting(rule -> rule.compartimentoId()).containsExactlyInAnyOrder("ENV-R2", "ENV-R3");
        assertThat(copies).extracting(rule -> rule.id()).doesNotContain(original.id());
        assertThat(service.get(original.id()).compartimentoId()).isEqualTo("ENV-R1");
        copies.forEach(copy -> assertThat(copy.configuracao()).isEqualTo(original.configuracao()));

        copies.forEach(copy -> service.setActive(copy.id(), false));
        assertThatThrownBy(() -> service.copyToRooms(original.id(), new CopyRequest(List.of("ENV-R2", "MISSING-ROOM"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select count(*) from regra_ambiente where compartimento_id='ENV-R2' and ativo", Integer.class)).isZero();
    }

    private RuleRequest request(String name, String scope, String campus, String room, boolean standard) {
        return new RuleRequest(name, null, "ILUMINACAO", true, standard, scope, campus, room,
                "America/Sao_Paulo", EnvironmentRuleService.TEN_MINUTES_PRESET, List.of(1,2,3,4,5,6,7),
                null, null, "IGNORE", "NONE", true,
                json.createObjectNode().put("satisfied", 0).put("notSatisfied", 0),
                json.createObjectNode().set("conditions", json.createArrayNode()));
    }

    private RuleRequest withSchedule(RuleRequest source, String cron, String timezone) {
        return new RuleRequest(source.nome(), source.descricao(), source.categoria(), source.ativo(), source.padrao(), source.escopo(),
                source.campusNome(), source.compartimentoId(), timezone, cron, source.diasSemana(), source.vigenciaInicio(),
                source.vigenciaFim(), source.politicaOcupacao(), source.politicaBeneficiario(), source.monitorarSemPontos(),
                source.pontuacao(), source.configuracao());
    }

    private RuleRequest withConfiguration(RuleRequest source, com.fasterxml.jackson.databind.JsonNode configuration) {
        return new RuleRequest(source.nome(), source.descricao(), source.categoria(), source.ativo(), source.padrao(), source.escopo(),
                source.campusNome(), source.compartimentoId(), source.timezone(), source.cronExpression(), source.diasSemana(),
                source.vigenciaInicio(), source.vigenciaFim(), source.politicaOcupacao(), source.politicaBeneficiario(),
                source.monitorarSemPontos(), source.pontuacao(), configuration);
    }
}
