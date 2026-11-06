package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleRequest;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.TestRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "proben.sensors.seed-path=missing-test-sensors.json",
        "proben.security.bootstrap-admin.enabled=false",
        "proben.shared-context.collector.enabled=false",
        "proben.environment-rules.worker.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class EnvironmentRuleTemporalIntegrationTest {
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

    private UUID parameter;
    private String sensor;
    private String otherRoomSensor;
    private String category;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        String campus = "TEMP-C-" + suffix;
        String unit = "TEMP-U-" + suffix;
        String building = "TEMP-B-" + suffix;
        String type = "TEMP-T-" + suffix;
        sensor = "TEMP-S-" + suffix;
        otherRoomSensor = "TEMP-OTHER-S-" + suffix;
        category = "TEMP_" + suffix.replace("-", "").toUpperCase();
        parameter = UUID.randomUUID();

        jdbc.update("insert into campus(nome) values (?)", campus);
        jdbc.update("insert into unidade(nome) values (?)", unit);
        jdbc.update("insert into predio(id,campus_id,nome) values (?,?,?)", building, campus, building);
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values ('TEMP-R1',?,?,?,'Sala') on conflict(id) do nothing", building, unit, "Room 1");
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values ('TEMP-R2',?,?,?,'Sala') on conflict(id) do nothing", building, unit, "Room 2");
        jdbc.update("insert into tipo_de_sensor(nome) values (?)", type);
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,descricao,ativo) values (?,'NUMERIC','C',?,'temperature','Temperature',true)", parameter, type);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,compartimento_id,ativo) values (?,?,?,'TEMP-R1',true)", sensor, sensor, type);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,compartimento_id,ativo) values (?,?,?,'TEMP-R2',true)", otherRoomSensor, otherRoomSensor, type);
    }

    @Test
    void missingRequiredMeasurementIsSkippedAndNeverBecomesZero() {
        var result = evaluate(rule(600, sensor));
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_DATA");
        assertThat(result.pontosCalculados()).isZero();
        assertThat(result.evidencias().path("temperature").has("value")).isFalse();
        assertThat(result.motivo()).contains("missing");
    }

    @Test
    void nullMeasurementValueIsSkippedAndNeverBecomesZero() {
        Instant at = Instant.now().minusSeconds(2);
        measurement(sensor, parameter, at.minusSeconds(1), null);
        var result = service.test(rule(600, sensor).id(), new TestRequest("TEMP-R1", at));
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_DATA");
        assertThat(result.pontosCalculados()).isZero();
        assertThat(result.evidencias().path("temperature").has("value")).isFalse();
    }

    @Test
    void staleMeasurementIsRejectedWithTemporalEvidence() {
        Instant at = Instant.now().minusSeconds(2);
        measurement(sensor, parameter, at.minusSeconds(61), new BigDecimal("23.5"));
        var result = service.test(rule(60, sensor).id(), new TestRequest("TEMP-R1", at));
        assertThat(result.resultado()).isEqualTo("SKIPPED_STALE_DATA");
        assertThat(result.pontosCalculados()).isZero();
        assertEvidence(result.evidencias().path("temperature"), "LOCAL", 60, "SKIPPED_STALE_DATA");
    }

    @Test
    void futureObservationIsIgnoredAndLatestPastObservationIsUsed() {
        Instant at = Instant.now().minusSeconds(10);
        measurement(sensor, parameter, at.minusSeconds(5), new BigDecimal("24"));
        measurement(sensor, parameter, at.plusSeconds(5), new BigDecimal("99"));
        var result = service.test(rule(60, sensor).id(), new TestRequest("TEMP-R1", at));
        assertThat(result.resultado()).isEqualTo("SATISFIED");
        assertThat(result.evidencias().path("temperature").path("value").decimalValue()).isEqualByComparingTo("24");
    }

    @Test
    void measurementInsideAndExactlyAtValidityBoundaryIsAccepted() {
        Instant at = Instant.now().minusSeconds(2).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        measurement(sensor, parameter, at.minusSeconds(60), new BigDecimal("24"));
        var boundary = service.test(rule(60, sensor).id(), new TestRequest("TEMP-R1", at));
        assertThat(boundary.resultado()).isEqualTo("SATISFIED");
        assertThat(boundary.pontosCalculados()).isEqualTo(7);
    }

    @Test
    void newestEligibleMeasurementWinsAmongMultipleMeasurements() {
        Instant at = Instant.now().minusSeconds(2);
        measurement(sensor, parameter, at.minusSeconds(30), new BigDecimal("10"));
        measurement(sensor, parameter, at.minusSeconds(5), new BigDecimal("24"));
        var result = service.test(rule(60, sensor).id(), new TestRequest("TEMP-R1", at));
        assertThat(result.resultado()).isEqualTo("SATISFIED");
        assertThat(result.evidencias().path("temperature").path("value").decimalValue()).isEqualByComparingTo("24");
    }

    @Test
    void sensorFromAnotherRoomIsNeverUsed() {
        Instant at = Instant.now().minusSeconds(2);
        measurement(otherRoomSensor, parameter, at.minusSeconds(1), new BigDecimal("24"));
        var result = service.test(rule(60, null).id(), new TestRequest("TEMP-R1", at));
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_DATA");
        assertThat(result.pontosCalculados()).isZero();
    }

    @Test
    void inactiveSensorOrParameterIsNeverUsed() {
        Instant at = Instant.now().minusSeconds(2);
        measurement(sensor, parameter, at.minusSeconds(1), new BigDecimal("24"));
        var rule = rule(60, sensor);
        jdbc.update("update sensor set ativo=false where external_id=?", sensor);
        assertThat(service.test(rule.id(), new TestRequest("TEMP-R1", at)).resultado()).isEqualTo("SKIPPED_NO_DATA");
        jdbc.update("update sensor set ativo=true where external_id=?", sensor);
        jdbc.update("update parametro_def set ativo=false where id=?", parameter);
        assertThat(service.test(rule.id(), new TestRequest("TEMP-R1", at)).resultado()).isEqualTo("SKIPPED_NO_DATA");
    }

    @Test
    void parameterIncompatibleWithSensorTypeIsRejectedAtConfigurationTime() {
        String incompatibleType = "OTHER-" + UUID.randomUUID();
        UUID incompatible = UUID.randomUUID();
        jdbc.update("insert into tipo_de_sensor(nome) values (?)", incompatibleType);
        jdbc.update("insert into parametro_def(id,data_type,tipo_nome,nome,ativo) values (?,'NUMERIC',?,'other',true)", incompatible, incompatibleType);
        assertThatThrownBy(() -> service.create(request(configuration(60, sensor, incompatible))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatible");
    }

    @Test
    void persistedEvidenceContainsMeasurementProvenanceAndTemporalContract() {
        Instant at = Instant.now().minusSeconds(2);
        measurement(sensor, parameter, at.minusSeconds(1), new BigDecimal("24"));
        var rule = rule(60, sensor);
        var execution = service.manual(rule.id(), "TEMP-R1", at);
        assertThat(worker.processAvailable()).isEqualTo(1);
        var completed = service.execution(execution.id());
        assertThat(completed.resultado()).isEqualTo("SATISFIED");
        assertEvidence(completed.evidencias().path("temperature"), "LOCAL", 60, null);
        assertThat(completed.evidencias().path("temperature").path("parameterId").asText()).isEqualTo(parameter.toString());
        assertThat(completed.evidencias().path("temperature").path("unit").asText()).isEqualTo("C");
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.TestResponse evaluate(
            com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule) {
        return service.test(rule.id(), new TestRequest("TEMP-R1", Instant.now().minusSeconds(2)));
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule(int maxAge, String sensorId) {
        return service.create(request(configuration(maxAge, sensorId, parameter)));
    }

    private RuleRequest request(ObjectNode configuration) {
        return new RuleRequest("Temporal rule", null, category, true, true, "GLOBAL", null, null,
                "America/Sao_Paulo", EnvironmentRuleService.TEN_MINUTES_PRESET, List.of(1, 2, 3, 4, 5, 6, 7),
                null, null, "IGNORE", "NONE", false,
                json.createObjectNode().put("satisfied", 7).put("notSatisfied", 3), configuration);
    }

    private ObjectNode configuration(int maxAge, String sensorId, UUID parameterId) {
        ObjectNode condition = json.createObjectNode().put("alias", "temperature")
                .put("parametroDefId", parameterId.toString()).put("source", "LOCAL")
                .put("operator", "GTE").put("value", 20).put("maxAgeSeconds", maxAge);
        if (sensorId != null) condition.put("sensorExternalId", sensorId);
        ObjectNode configuration = json.createObjectNode().put("combination", "AND");
        configuration.putArray("conditions").add(condition);
        return configuration;
    }

    private void measurement(String sensorId, UUID parameterId, Instant at, BigDecimal value) {
        UUID measurement = UUID.randomUUID();
        jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,?,?,?)",
                measurement, sensorId, Timestamp.from(at), Timestamp.from(at), "TEST");
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,numeric_value) values (?,?,?,?)",
                UUID.randomUUID(), measurement, parameterId, value);
    }

    private void assertEvidence(com.fasterxml.jackson.databind.JsonNode evidence, String source, int maxAge, String rejection) {
        assertThat(evidence.path("sensorExternalId").asText()).isEqualTo(sensor);
        assertThat(evidence.path("observedAt").asText()).isNotBlank();
        assertThat(evidence.path("source").asText()).isEqualTo(source);
        assertThat(evidence.path("maxAgeSeconds").asInt()).isEqualTo(maxAge);
        if (rejection == null) assertThat(evidence.has("rejectionReason")).isFalse();
        else assertThat(evidence.path("rejectionReason").asText()).isEqualTo(rejection);
    }
}
