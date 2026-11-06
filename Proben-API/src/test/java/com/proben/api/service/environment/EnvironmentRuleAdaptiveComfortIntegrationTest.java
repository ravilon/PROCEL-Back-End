package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleRequest;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.TestRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.time.temporal.ChronoUnit;
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
class EnvironmentRuleAdaptiveComfortIntegrationTest {
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

    private String room;
    private String localSensor;
    private String sharedSensor;
    private UUID internalTemperature;
    private UUID airConditioning;
    private UUID occupancy;
    private UUID externalTemperature;
    private UUID integration;
    private String category;
    private Instant at;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        String campus = "COMFORT-C-" + suffix;
        String unit = "COMFORT-U-" + suffix;
        String building = "COMFORT-B-" + suffix;
        String localType = "COMFORT-LOCAL-" + suffix;
        String sharedType = "COMFORT-SHARED-" + suffix;
        room = "COMFORT-R-" + suffix;
        localSensor = "COMFORT-LS-" + suffix;
        sharedSensor = "COMFORT-SS-" + suffix;
        category = "COMFORT_" + suffix.replace("-", "").toUpperCase();
        internalTemperature = UUID.randomUUID();
        airConditioning = UUID.randomUUID();
        occupancy = UUID.randomUUID();
        externalTemperature = UUID.randomUUID();
        UUID context = UUID.randomUUID();
        integration = UUID.randomUUID();
        at = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.SECONDS);

        jdbc.update("insert into campus(nome,latitude,longitude) values (?,10,20)", campus);
        jdbc.update("insert into unidade(nome) values (?)", unit);
        jdbc.update("insert into predio(id,campus_id,nome) values (?,?,?)", building, campus, building);
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values (?,?,?,?,'Sala')", room, building, unit, room);
        jdbc.update("insert into tipo_de_sensor(nome) values (?),(?)", localType, sharedType);
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,ativo) values (?,'NUMERIC','C',?,'temperatura_interna',true)", internalTemperature, localType);
        jdbc.update("insert into parametro_def(id,data_type,tipo_nome,nome,ativo) values (?,'BOOLEAN',?,'ar_condicionado_ligado',true)", airConditioning, localType);
        jdbc.update("insert into parametro_def(id,data_type,tipo_nome,nome,ativo) values (?,'BOOLEAN',?,'ocupacao',true)", occupancy, localType);
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,ativo) values (?,'NUMERIC','C',?,'temperatura_externa',true)", externalTemperature, sharedType);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,compartimento_id,ativo) values (?,?,?, ?,true)", localSensor, localSensor, localType, room);
        jdbc.update("insert into contexto_compartilhado(id,nome,ativo,location_mode,campus_nome) values (?,?,true,'CAMPUS',?)", context, context.toString(), campus);
        jdbc.update("insert into contexto_compartilhado_compartimento(contexto_id,compartimento_id,ativo) values (?,?,true)", context, room);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) values (?,?,?,?,true)", sharedSensor, sharedSensor, sharedType, context);
        jdbc.update("insert into contexto_fonte_selecao(compartimento_id,parametro_def_id,sensor_external_id,validade_segundos) values (?,?,?,3600)", room, externalTemperature, sharedSensor);
        jdbc.update("insert into contexto_integracao_externa(id,contexto_id,sensor_external_id,nome,ativo,endpoint_url,temperature_json_pointer) values (?,?,?,?,true,'https://invalid.local','/temperature')", integration, context, sharedSensor, integration.toString());
        String hash = jdbc.queryForObject("select encode(digest(concat_ws('|',latitude,longitude),'sha256'),'hex') from campus where nome=?", String.class, campus);
        for (int hour = 121; hour >= 0; hour--) sharedMeasurement(at.minus(hour, ChronoUnit.HOURS), new BigDecimal("20"), hash, campus);
    }

    @ParameterizedTest(name = "internal {0} -> {1} with {2} points")
    @CsvSource({
            "20.49,NOT_SATISFIED,3",
            "20.50,SATISFIED,9",
            "24.00,SATISFIED,9",
            "27.50,SATISFIED,9",
            "27.51,NOT_SATISFIED,3"
    })
    void evaluatesCompleteAdaptiveBandWithInclusiveLimits(String internal, String expected, int points) {
        localMeasurement(new BigDecimal(internal), true);
        var result = evaluate(rule());
        assertThat(result.resultado()).isEqualTo(expected);
        assertThat(result.pontosCalculados()).isEqualTo(points);
        assertThat(result.evidencias().path("comfortBand").path("neutral").decimalValue()).isEqualByComparingTo("24.00");
        assertThat(result.evidencias().path("comfortBand").path("lower").decimalValue()).isEqualByComparingTo("20.50");
        assertThat(result.evidencias().path("comfortBand").path("upper").decimalValue()).isEqualByComparingTo("27.50");
    }

    @Test
    void airConditioningConditionRemainsRequiredByAdaptivePreset() {
        localMeasurement(new BigDecimal("24"), false);
        var result = evaluate(rule());
        assertThat(result.resultado()).isEqualTo("NOT_SATISFIED");
        assertThat(result.pontosCalculados()).isEqualTo(3);
    }

    @Test
    void missingAnyMandatoryFactSkipsWithoutPoints() {
        var result = evaluate(rule());
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_DATA");
        assertThat(result.pontosCalculados()).isZero();
    }

    @Test
    void occupiedRoomIsEvaluatedWhenAdaptiveRuleRequiresOccupancy() {
        localMeasurement(new BigDecimal("24"), true, true);
        var result = evaluate(rule("REQUIRE_OCCUPIED"));
        assertThat(result.resultado()).isEqualTo("SATISFIED");
        assertThat(result.pontosCalculados()).isEqualTo(9);
        assertThat(result.ocupacao().path("occupied").asBoolean()).isTrue();
    }

    @Test
    void unoccupiedRoomIsSkippedWhenAdaptiveRuleRequiresOccupancy() {
        localMeasurement(new BigDecimal("24"), true, false);
        var result = evaluate(rule("REQUIRE_OCCUPIED"));
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_OCCUPANCY");
        assertThat(result.pontosCalculados()).isZero();
        assertThat(result.ocupacao().path("occupied").asBoolean()).isFalse();
    }

    @Test
    void persistedExecutionSnapshotsInputsAndCalculatedValues() {
        localMeasurement(new BigDecimal("24"), true);
        var rule = rule();
        var pending = service.manual(rule.id(), room, at);
        assertThat(worker.processAvailable()).isEqualTo(1);
        var execution = service.execution(pending.id());
        assertThat(execution.resultado()).isEqualTo("SATISFIED");
        assertThat(execution.evidencias().path("Ti").path("value").decimalValue()).isEqualByComparingTo("24");
        assertThat(execution.evidencias().path("Temed").path("value").decimalValue()).isEqualByComparingTo("20");
        assertThat(execution.evidencias().path("AC").path("value").asBoolean()).isTrue();
        assertThat(execution.evidencias().path("comfortBand").path("lower").decimalValue()).isEqualByComparingTo("20.50");
        assertThat(execution.pontosCalculados()).isEqualTo(9);
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule() {
        return rule("IGNORE");
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule(String occupancyPolicy) {
        ObjectNode config = json.createObjectNode().put("preset", "ADAPTIVE_COMFORT")
                .put("lowerOffset", 3.5).put("upperOffset", 3.5).put("combination", "AND")
                .put("occupancyParametroDefId", occupancy.toString()).put("occupancyMaxAgeSeconds", 600);
        var conditions = config.putArray("conditions");
        conditions.add(json.createObjectNode().put("alias", "Ti").put("parametroDefId", internalTemperature.toString())
                .put("sensorExternalId", localSensor).put("source", "LOCAL").put("operator", "GTE").put("maxAgeSeconds", 600));
        conditions.add(json.createObjectNode().put("alias", "Temed").put("parametroDefId", externalTemperature.toString())
                .put("source", "SHARED").put("operator", "GTE").put("sevenDayAverage", true));
        conditions.add(json.createObjectNode().put("alias", "AC").put("parametroDefId", airConditioning.toString())
                .put("sensorExternalId", localSensor).put("source", "LOCAL").put("operator", "EQ").put("value", true).put("maxAgeSeconds", 600));
        return service.create(new RuleRequest("Adaptive comfort", null, category, true, true, "GLOBAL", null, null,
                "America/Sao_Paulo", EnvironmentRuleService.TEN_MINUTES_PRESET, List.of(1, 2, 3, 4, 5, 6, 7),
                null, null, occupancyPolicy, "NONE", false,
                json.createObjectNode().put("satisfied", 9).put("notSatisfied", 3), config));
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.TestResponse evaluate(
            com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule) {
        return service.test(rule.id(), new TestRequest(room, at));
    }

    private void localMeasurement(BigDecimal temperature, boolean acOn) {
        localMeasurement(temperature, acOn, null);
    }

    private void localMeasurement(BigDecimal temperature, boolean acOn, Boolean occupied) {
        UUID measurement = UUID.randomUUID();
        jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,?,?,?)", measurement, localSensor, Timestamp.from(at.minusSeconds(1)), Timestamp.from(at), "TEST_LOCAL");
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,numeric_value) values (?,?,?,?)", UUID.randomUUID(), measurement, internalTemperature, temperature);
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,boolean_value) values (?,?,?,?)", UUID.randomUUID(), measurement, airConditioning, acOn);
        if (occupied != null) {
            jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,boolean_value) values (?,?,?,?)", UUID.randomUUID(), measurement, occupancy, occupied);
        }
    }

    private void sharedMeasurement(Instant observed, BigDecimal value, String hash, String campus) {
        UUID measurement = UUID.randomUUID();
        jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,?,?,?)", measurement, sharedSensor, Timestamp.from(observed), Timestamp.from(observed), "TEST_SHARED");
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,numeric_value) values (?,?,?,?)", UUID.randomUUID(), measurement, externalTemperature, value);
        jdbc.update("insert into contexto_coleta_execucao(id,integracao_id,started_at,finished_at,outcome,observation_at,medicao_id,latitude,longitude,location_source,campus_nome,location_hash) values (?,?,now(),now(),'NEW_MEASUREMENT',?,?,10,20,'CAMPUS',?,?)", UUID.randomUUID(), integration, Timestamp.from(observed), measurement, campus, hash);
    }
}
