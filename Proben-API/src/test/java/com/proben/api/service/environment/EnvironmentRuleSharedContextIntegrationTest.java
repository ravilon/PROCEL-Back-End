package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleRequest;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.TestRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class EnvironmentRuleSharedContextIntegrationTest {
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

    private UUID parameter;
    private UUID context;
    private UUID integration;
    private String sensor;
    private String category;
    private String campus;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        campus = "SHARED-C-" + suffix;
        String unit = "SHARED-U-" + suffix;
        String building = "SHARED-B-" + suffix;
        String type = "SHARED-T-" + suffix;
        sensor = "SHARED-S-" + suffix;
        category = "SHARED_" + suffix.replace("-", "").toUpperCase();
        parameter = UUID.randomUUID();
        context = UUID.randomUUID();
        integration = UUID.randomUUID();

        jdbc.update("insert into campus(nome,latitude,longitude) values (?,10.000000,20.000000)", campus);
        jdbc.update("insert into unidade(nome) values (?)", unit);
        jdbc.update("insert into predio(id,campus_id,nome) values (?,?,?)", building, campus, building);
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values (?,?,?,?,'Sala')", "SHARED-R1-" + suffix, building, unit, "Room 1");
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values (?,?,?,?,'Sala')", "SHARED-R2-" + suffix, building, unit, "Room 2");
        jdbc.update("insert into tipo_de_sensor(nome) values (?)", type);
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,descricao,ativo) values (?,'NUMERIC','C',?,'temperatura_externa','External temperature',true)", parameter, type);
        jdbc.update("insert into contexto_compartilhado(id,nome,ativo,location_mode,campus_nome,timezone) values (?,?,true,'CAMPUS',?,'America/Sao_Paulo')", context, context.toString(), campus);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) values (?,?,?,?,true)", sensor, sensor, type, context);
        jdbc.update("insert into contexto_integracao_externa(id,contexto_id,sensor_external_id,nome,ativo,endpoint_url,temperature_json_pointer) values (?,?,?,?,true,'https://invalid.local','/temperature')", integration, context, sensor, integration.toString());
    }

    @Test
    void ruleUsesOnlyTheExplicitlySelectedSharedSource() {
        String room = room(1);
        link(room, sensor, 3600);
        Instant at = Instant.now().minusSeconds(2);
        sharedMeasurement(sensor, integration, at.minusSeconds(5), new BigDecimal("18"), currentHash());

        String unselectedSensor = secondSharedSource();
        UUID unselectedIntegration = integrationFor(unselectedSensor);
        sharedMeasurement(unselectedSensor, unselectedIntegration, at.minusSeconds(1), new BigDecimal("99"), currentHash());

        var result = evaluate(rule(false), room, at);
        assertThat(result.resultado()).isEqualTo("SATISFIED");
        assertThat(result.evidencias().path("external").path("value").decimalValue()).isEqualByComparingTo("18");
        assertThat(result.evidencias().path("external").path("sensorExternalId").asText()).isEqualTo(sensor);
        assertThat(result.evidencias().path("external").path("origin").asText()).isEqualTo("SHARED");
    }

    @Test
    void oneSharedObservationServesSeveralRoomsWithoutReplication() {
        String first = room(1);
        String second = room(2);
        link(first, sensor, 3600);
        link(second, sensor, 3600);
        Instant at = Instant.now().minusSeconds(2);
        sharedMeasurement(sensor, integration, at.minusSeconds(1), new BigDecimal("18"), currentHash());
        var rule = rule(false);

        assertThat(evaluate(rule, first, at).resultado()).isEqualTo("SATISFIED");
        assertThat(evaluate(rule, second, at).resultado()).isEqualTo("SATISFIED");
        assertThat(jdbc.queryForObject("select count(*) from medicao where sensor_external_id=?", Integer.class, sensor)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from parametro_valor where parametro_def_id=?", Integer.class, parameter)).isEqualTo(1);
    }

    @ParameterizedTest(name = "inactive {0} prevents shared evidence")
    @ValueSource(strings = {"CONTEXT", "LINK", "SENSOR", "INTEGRATION"})
    void inactiveSharedDependencyPreventsUse(String dependency) {
        String room = room(1);
        link(room, sensor, 3600);
        Instant at = Instant.now().minusSeconds(2);
        sharedMeasurement(sensor, integration, at.minusSeconds(1), new BigDecimal("18"), currentHash());
        var rule = rule(false);
        switch (dependency) {
            case "CONTEXT" -> jdbc.update("update contexto_compartilhado set ativo=false where id=?", context);
            case "LINK" -> jdbc.update("update contexto_compartilhado_compartimento set ativo=false where contexto_id=? and compartimento_id=?", context, room);
            case "SENSOR" -> jdbc.update("update sensor set ativo=false where external_id=?", sensor);
            case "INTEGRATION" -> jdbc.update("update contexto_integracao_externa set ativo=false where id=?", integration);
            default -> throw new IllegalArgumentException(dependency);
        }
        var result = evaluate(rule, room, at);
        assertThat(result.resultado()).isEqualTo("SKIPPED_NO_DATA");
        assertThat(result.pontosCalculados()).isZero();
    }

    @Test
    void coordinateChangeRejectsOldHashAndNewLocationRestoresAvailability() {
        String room = room(1);
        link(room, sensor, 3600);
        Instant at = Instant.now().minusSeconds(2);
        String oldHash = currentHash();
        sharedMeasurement(sensor, integration, at.minusSeconds(2), new BigDecimal("18"), oldHash);
        var rule = rule(false);
        assertThat(evaluate(rule, room, at).resultado()).isEqualTo("SATISFIED");

        jdbc.update("update campus set latitude=11.000000,longitude=21.000000 where nome=?", campus);
        assertThat(evaluate(rule, room, at).resultado()).isEqualTo("SKIPPED_NO_DATA");
        String newHash = currentHash();
        sharedMeasurement(sensor, integration, at.minusSeconds(1), new BigDecimal("19"), newHash);
        var restored = evaluate(rule, room, at);
        assertThat(restored.resultado()).isEqualTo("SATISFIED");
        assertThat(restored.evidencias().path("external").path("locationHash").asText()).isEqualTo(newHash);
        assertThat(newHash).isNotEqualTo(oldHash);
    }

    @Test
    void absentStaleAndFutureExternalValuesNeverBecomeZero() {
        String room = room(1);
        link(room, sensor, 60);
        Instant at = Instant.now().minusSeconds(10);
        var rule = rule(false);
        assertThat(evaluate(rule, room, at).resultado()).isEqualTo("SKIPPED_NO_DATA");
        sharedMeasurement(sensor, integration, at.minusSeconds(61), new BigDecimal("18"), currentHash());
        assertThat(evaluate(rule, room, at).resultado()).isEqualTo("SKIPPED_STALE_DATA");
        sharedMeasurement(sensor, integration, at.plusSeconds(1), new BigDecimal("99"), currentHash());
        var result = evaluate(rule, room, at);
        assertThat(result.resultado()).isEqualTo("SKIPPED_STALE_DATA");
        assertThat(result.pontosCalculados()).isZero();
    }

    @Test
    void sevenDayAverageUses168HoursWeightingDeduplicationAndOneHourGapCap() {
        String room = room(1);
        link(room, sensor, 3600);
        Instant end = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        String hash = currentHash();
        for (int hour = 121; hour >= 0; hour--) {
            Instant observed = end.minus(hour, ChronoUnit.HOURS);
            sharedMeasurement(sensor, integration, observed, hour > 60 ? new BigDecimal("10") : new BigDecimal("20"), hash);
        }
        sharedMeasurement(sensor, integration, end.minus(60, ChronoUnit.HOURS), new BigDecimal("20"), hash);
        var result = evaluate(rule(true), room, end);
        var evidence = result.evidencias().path("external");
        assertThat(result.resultado()).isEqualTo("SATISFIED");
        assertThat(Instant.ofEpochSecond(evidence.path("windowStart").asLong())).isEqualTo(end.minus(168, ChronoUnit.HOURS));
        assertThat(evidence.path("coveredHours").decimalValue()).isEqualByComparingTo("121.00");
        assertThat(evidence.path("distinctObservations").asInt()).isEqualTo(122);
        assertThat(evidence.path("method").asText()).contains("duplicate timestamps collapsed").contains("gaps capped at 1h");
        assertThat(evidence.path("value").decimalValue()).isBetween(new BigDecimal("14.9"), new BigDecimal("15.1"));
    }

    @Test
    void insufficientSevenDayCoverageIsExplicitAndAwardsNoPoints() {
        String room = room(1);
        link(room, sensor, 3600);
        Instant end = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        String hash = currentHash();
        for (int hour = 10; hour >= 0; hour--) sharedMeasurement(sensor, integration, end.minus(hour, ChronoUnit.HOURS), new BigDecimal("18"), hash);
        var result = evaluate(rule(true), room, end);
        assertThat(result.resultado()).isEqualTo("SKIPPED_INSUFFICIENT_COVERAGE");
        assertThat(result.pontosCalculados()).isZero();
        assertThat(result.evidencias().path("external").path("coveredHours").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(result.evidencias().path("external").path("requiredHours").decimalValue()).isEqualByComparingTo("120");
    }

    private String room(int number) {
        return jdbc.queryForObject("select id from compartimento where nome=? and predio_id in (select id from predio where campus_id=?)", String.class, "Room " + number, campus);
    }

    private void link(String room, String selectedSensor, int validity) {
        jdbc.update("insert into contexto_compartilhado_compartimento(contexto_id,compartimento_id,ativo) values (?,?,true) on conflict do nothing", context, room);
        jdbc.update("insert into contexto_fonte_selecao(compartimento_id,parametro_def_id,sensor_external_id,validade_segundos) values (?,?,?,?)", room, parameter, selectedSensor, validity);
    }

    private String secondSharedSource() {
        String second = "UNSELECTED-" + UUID.randomUUID();
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) select ?,?,tipo_nome,?,true from sensor where external_id=?", second, second, context, sensor);
        integrationFor(second);
        return second;
    }

    private UUID integrationFor(String selectedSensor) {
        var existing = jdbc.query("select id from contexto_integracao_externa where sensor_external_id=?", (rs, row) -> rs.getObject(1, UUID.class), selectedSensor);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID id = UUID.randomUUID();
        jdbc.update("insert into contexto_integracao_externa(id,contexto_id,sensor_external_id,nome,ativo,endpoint_url,temperature_json_pointer) values (?,?,?,?,true,'https://invalid.local','/temperature')", id, context, selectedSensor, id.toString());
        return id;
    }

    private void sharedMeasurement(String selectedSensor, UUID selectedIntegration, Instant at, BigDecimal value, String hash) {
        UUID measurement = UUID.randomUUID();
        jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,?,?,?)", measurement, selectedSensor, Timestamp.from(at), Timestamp.from(at), "TEST_SHARED");
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,numeric_value) values (?,?,?,?)", UUID.randomUUID(), measurement, parameter, value);
        jdbc.update("insert into contexto_coleta_execucao(id,integracao_id,started_at,finished_at,outcome,observation_at,medicao_id,latitude,longitude,location_source,campus_nome,location_hash) values (?,?,now(),now(),'NEW_MEASUREMENT',?,?,(select latitude from campus where nome=?),(select longitude from campus where nome=?),'CAMPUS',?,?)", UUID.randomUUID(), selectedIntegration, Timestamp.from(at), measurement, campus, campus, campus, hash);
    }

    private String currentHash() {
        return jdbc.queryForObject("select encode(digest(concat_ws('|',latitude,longitude),'sha256'),'hex') from campus where nome=?", String.class, campus);
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule(boolean average) {
        ObjectNode condition = json.createObjectNode().put("alias", "external")
                .put("parametroDefId", parameter.toString()).put("source", "SHARED")
                .put("operator", "GTE").put("value", 0).put("sevenDayAverage", average);
        ObjectNode configuration = json.createObjectNode().put("combination", "AND");
        configuration.putArray("conditions").add(condition);
        return service.create(new RuleRequest("Shared rule", null, category, true, true, "GLOBAL", null, null,
                "America/Sao_Paulo", EnvironmentRuleService.TEN_MINUTES_PRESET, List.of(1, 2, 3, 4, 5, 6, 7),
                null, null, "IGNORE", "NONE", false,
                json.createObjectNode().put("satisfied", 11).put("notSatisfied", 2), configuration));
    }

    private com.proben.api.dto.environment.EnvironmentRuleDTOs.TestResponse evaluate(
            com.proben.api.dto.environment.EnvironmentRuleDTOs.RuleResponse rule, String room, Instant at) {
        return service.test(rule.id(), new TestRequest(room, at));
    }
}
