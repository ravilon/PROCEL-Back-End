package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleRequest;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "proben.sensors.seed-path=missing-test-sensors.json",
        "proben.security.bootstrap-admin.enabled=false",
        "proben.shared-context.collector.enabled=false",
        "proben.environment-rules.worker.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class EnvironmentRuleHistoryIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

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
    private String sensor;
    private UUID parameter;
    private String category;
    private Instant at;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        campus = "HISTORY-C-" + suffix;
        String unit = "HISTORY-U-" + suffix;
        String building = "HISTORY-B-" + suffix;
        String type = "HISTORY-T-" + suffix;
        room = "HISTORY-R-" + suffix;
        sensor = "HISTORY-S-" + suffix;
        category = "HISTORY_" + suffix.replace("-", "").toUpperCase();
        parameter = UUID.randomUUID();
        at = Instant.now().minusSeconds(2).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("insert into campus(nome) values (?)", campus);
        jdbc.update("insert into unidade(nome) values (?)", unit);
        jdbc.update("insert into predio(id,campus_id,nome) values (?,?,?)", building, campus, building);
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values (?,?,?,?,'Sala')", room, building, unit, room);
        jdbc.update("insert into tipo_de_sensor(nome) values (?)", type);
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,ativo) values (?,'NUMERIC','C',?,'temperature',true)", parameter, type);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,compartimento_id,ativo) values (?,?,?,?,true)", sensor, sensor, type, room);
    }

    @Test
    void completedHistoryPreservesIdentityTimingFactsContextAndOutcome() {
        measurement(at.minusSeconds(1), new BigDecimal("24"));
        var rule = service.create(request("Original", 5, sensor, 600));
        var pending = service.manual(rule.id(), room, at);
        assertThat(worker.processAvailable()).isEqualTo(1);
        var execution = service.execution(pending.id());
        JsonNode snapshot = rawSnapshot(execution.id());

        assertThat(execution.regraId()).isEqualTo(rule.id());
        assertThat(execution.regraVersao()).isEqualTo(1);
        assertThat(snapshot.path("nome").asText()).isEqualTo("Original");
        assertThat(snapshot.path("configuracao").path("conditions").get(0).path("sensorExternalId").asText()).isEqualTo(sensor);
        assertThat(execution.compartimentoId()).isEqualTo(room);
        assertThat(execution.categoria()).isEqualTo(category);
        assertThat(execution.origem()).isEqualTo("GLOBAL_DEFAULT");
        assertThat(execution.tipoExecucao()).isEqualTo("MANUAL");
        assertThat(execution.instantePrevisto()).isEqualTo(at);
        assertThat(execution.instanteReal()).isNotNull();
        assertThat(execution.timezone()).isEqualTo("America/Sao_Paulo");
        assertThat(execution.evidencias().path("temperature").path("sensorExternalId").asText()).isEqualTo(sensor);
        assertThat(execution.evidencias().path("temperature").path("observedAt").asText()).isNotBlank();
        assertThat(execution.evidencias().path("_execution").path("campus").asText()).isEqualTo(campus);
        assertThat(execution.ocupacao().path("occupied").asBoolean()).isFalse();
        assertThat(execution.contextoAcademico().path("compartimentoId").asText()).isEqualTo(room);
        assertThat(execution.beneficiarios()).isEmpty();
        assertThat(execution.resultado()).isEqualTo("SATISFIED");
        assertThat(execution.pontosCalculados()).isEqualTo(5);
        assertThat(execution.lancamentos()).isEmpty();
        assertThat(execution.attempts()).isEqualTo(1);
        assertThat(execution.durationMs()).isNotNegative();
    }

    @Test
    void editingRuleCreatesNewVersionWithoutRewritingPreviousSnapshot() {
        measurement(at.minusSeconds(1), new BigDecimal("24"));
        var original = service.create(request("Original", 5, sensor, 600));
        var oldPending = service.manual(original.id(), room, at);
        worker.processAvailable();
        JsonNode oldSnapshotBefore = rawSnapshot(oldPending.id()).deepCopy();
        JsonNode oldEvidenceBefore = service.execution(oldPending.id()).evidencias().deepCopy();

        var updated = service.update(original.id(), request("Edited", 9, sensor, 300));
        assertThat(updated.versao()).isEqualTo(2);
        var newPending = service.manual(updated.id(), room, at.plusSeconds(1));
        worker.processAvailable();

        assertThat(rawSnapshot(oldPending.id())).isEqualTo(oldSnapshotBefore);
        assertThat(service.execution(oldPending.id()).evidencias()).isEqualTo(oldEvidenceBefore);
        assertThat(service.execution(oldPending.id()).regraVersao()).isEqualTo(1);
        assertThat(rawSnapshot(oldPending.id()).path("nome").asText()).isEqualTo("Original");
        assertThat(rawSnapshot(oldPending.id()).path("pontuacao").path("satisfied").asInt()).isEqualTo(5);
        assertThat(rawSnapshot(oldPending.id()).path("configuracao").path("conditions").get(0).path("maxAgeSeconds").asInt()).isEqualTo(600);
        assertThat(service.execution(newPending.id()).regraVersao()).isEqualTo(2);
        assertThat(rawSnapshot(newPending.id()).path("nome").asText()).isEqualTo("Edited");
        assertThat(rawSnapshot(newPending.id()).path("pontuacao").path("satisfied").asInt()).isEqualTo(9);
        assertThat(rawSnapshot(newPending.id()).path("configuracao").path("conditions").get(0).path("maxAgeSeconds").asInt()).isEqualTo(300);
    }

    @Test
    void ignoredExecutionPreservesExplicitReasonAndRejectedEvidence() {
        measurement(at.minusSeconds(601), new BigDecimal("24"));
        var rule = service.create(request("Stale", 5, sensor, 600));
        var pending = service.manual(rule.id(), room, at);
        worker.processAvailable();
        var execution = service.execution(pending.id());
        assertThat(execution.resultado()).isEqualTo("SKIPPED_STALE_DATA");
        assertThat(execution.motivo()).contains("stale");
        assertThat(execution.evidencias().path("temperature").path("rejectionReason").asText()).isEqualTo("SKIPPED_STALE_DATA");
        assertThat(execution.pontosCalculados()).isZero();
        assertThat(execution.lancamentos()).isEmpty();
    }

    private RuleRequest request(String name, int points, String selectedSensor, int maxAge) {
        ObjectNode condition = json.createObjectNode().put("alias", "temperature")
                .put("parametroDefId", parameter.toString()).put("sensorExternalId", selectedSensor)
                .put("source", "LOCAL").put("operator", "GTE").put("value", 20).put("maxAgeSeconds", maxAge);
        ObjectNode config = json.createObjectNode().put("combination", "AND");
        config.putArray("conditions").add(condition);
        return new RuleRequest(name, null, category, true, true, "GLOBAL", null, null,
                "America/Sao_Paulo", EnvironmentRuleService.TEN_MINUTES_PRESET, List.of(1, 2, 3, 4, 5, 6, 7),
                null, null, "IGNORE", "NONE", false,
                json.createObjectNode().put("satisfied", points).put("notSatisfied", 0), config);
    }

    private void measurement(Instant observed, BigDecimal value) {
        UUID measurement = UUID.randomUUID();
        jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,?,?,?)", measurement, sensor, Timestamp.from(observed), Timestamp.from(observed), "TEST");
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,numeric_value) values (?,?,?,?)", UUID.randomUUID(), measurement, parameter, value);
    }

    private JsonNode rawSnapshot(UUID execution) {
        String raw = jdbc.queryForObject("select regra_snapshot::text from regra_ambiente_execucao where id=?", String.class, execution);
        try {
            return json.readTree(raw);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
