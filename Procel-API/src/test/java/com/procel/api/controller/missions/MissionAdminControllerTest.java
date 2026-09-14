package com.procel.api.controller.missions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MissionAdminControllerTest {

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

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbcTemplate;

    private UUID missionId;
    private UUID eventId;
    private UUID requestId;
    private UUID windowId;
    private UUID occurrenceId;
    private UUID measurementId;
    private String roomId;
    private String sensorId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        String suffix = UUID.randomUUID().toString();
        missionId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        windowId = UUID.randomUUID();
        occurrenceId = UUID.randomUUID();
        measurementId = UUID.randomUUID();
        roomId = "ROOM-ADMIN-" + suffix;
        sensorId = "SENSOR-ADMIN-" + suffix;

        seedRoomSensorAndMeasurement(suffix);
        seedMissionEventRequestWindowAndOccurrence();
    }

    @Test
    void analystCanConsultEventsRequestsWindowsOccurrencesAndWorkers() throws Exception {
        mvc.perform(get("/api/admin/missions/events?missionId={missionId}", missionId)
                        .with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].missaoId").value(missionId.toString()));

        mvc.perform(get("/api/admin/missions/evaluation-requests?status=PENDING")
                        .with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$.content[0].medicaoId").value(measurementId.toString()));

        mvc.perform(get("/api/admin/missions/windows?status=ABERTA&compartimentoId={roomId}", roomId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(windowId.toString()));

        mvc.perform(get("/api/admin/missions/occurrences?status=DETECTADO")
                        .with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(occurrenceId.toString()))
                .andExpect(jsonPath("$.content[0].sensorExternalId").value(sensorId));

        mvc.perform(get("/api/admin/missions/workers/status")
                        .with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.enabled").value(false))
                .andExpect(jsonPath("$.temporalWindows.enabled").value(false))
                .andExpect(jsonPath("$.drools.temporalDroolsEnabled").value(false))
                .andExpect(jsonPath("$.drools.temporalActivitiesEnabled").value(false));
    }

    @Test
    void adminCanConsultEvidenceAndOperatePersistedStatuses() throws Exception {
        mvc.perform(get("/api/admin/missions/windows/{windowId}/evidences", windowId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].papel").value("INICIO"));

        mvc.perform(post("/api/admin/missions/windows/{windowId}/invalidate", windowId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"admin regression\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALIDADA"))
                .andExpect(jsonPath("$.lastError").value("admin regression"));

        mvc.perform(get("/api/admin/missions/occurrences/{occurrenceId}/evidences", occurrenceId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].papel").value("CONDICAO"));

        mvc.perform(post("/api/admin/missions/occurrences/{occurrenceId}/status", occurrenceId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"CONFIRMADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));
    }

    @Test
    void nonAdminCannotOperateMissionRuntimeState() throws Exception {
        mvc.perform(post("/api/admin/missions/windows/{windowId}/expire", windowId)
                        .with(user("operador").roles("OPERADOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"not allowed\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/missions/workers/evaluation/run")
                        .with(user("operador").roles("OPERADOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/missions/windows")
                        .with(user("usuario").roles("USUARIO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void manualWorkerRunRespectsDisabledFlags() throws Exception {
        mvc.perform(post("/api/admin/missions/workers/evaluation/run")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.worker").value("evaluation"))
                .andExpect(jsonPath("$.processed").value(0));

        mvc.perform(post("/api/admin/missions/workers/temporal-windows/run")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.worker").value("temporal-windows"))
                .andExpect(jsonPath("$.processed").value(0));
    }

    private void seedRoomSensorAndMeasurement(String suffix) {
        String campus = "Campus admin " + suffix;
        String unidade = "Unidade admin " + suffix;
        String predio = campus + "|Predio";
        jdbcTemplate.update("insert into campus (nome) values (?)", campus);
        jdbcTemplate.update("insert into unidade (nome) values (?)", unidade);
        jdbcTemplate.update("insert into predio (id, campus_id, nome) values (?, ?, ?)", predio, campus, "Predio");
        jdbcTemplate.update("""
                insert into compartimento (id, predio_id, unidade_id, nome, tipo)
                values (?, ?, ?, 'Sala admin', 'Sala')
                """, roomId, predio, unidade);
        jdbcTemplate.update("insert into tipo_de_sensor (nome) values (?)", "TYPE-ADMIN-" + suffix);
        jdbcTemplate.update("""
                insert into sensor (external_id, nome, tipo_nome, compartimento_id, ativo)
                values (?, 'Sensor admin', ?, ?, true)
                """, sensorId, "TYPE-ADMIN-" + suffix, roomId);
        jdbcTemplate.update("""
                insert into medicao (id, sensor_external_id, timestamp, recebido_em, source)
                values (?, ?, ?, ?, 'TEST')
                """,
                measurementId,
                sensorId,
                Timestamp.from(Instant.parse("2026-09-14T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-14T10:00:01Z")));
    }

    private void seedMissionEventRequestWindowAndOccurrence() {
        jdbcTemplate.update("""
                insert into missao (id, titulo, descricao, tipo, value, ativo, created_at)
                values (?, 'Missao admin', 'Descricao', 'Individual', 10, true, now())
                """, missionId);
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'Evento admin', 'MEDICAO_RECEBIDA', 'DURACAO', 'ALL',
                        'SEM_ATRIBUICAO_AUTOMATICA', 1, 1, true, now(), now())
                """, eventId, missionId);
        jdbcTemplate.update("""
                insert into evento_avaliacao_request
                (id, medicao_id, status, attempts, available_at, created_at, updated_at)
                values (?, ?, 'PENDING', 0, now(), now(), now())
                """, requestId, measurementId);
        jdbcTemplate.update("""
                insert into evento_janela_avaliacao
                (id, evento_definicao_id, compartimento_id, status, inicio_em, fim_previsto_em, ultima_medicao_em,
                 proxima_avaliacao_em, attempts, chave_idempotencia, contexto_snapshot, created_at, updated_at)
                values (?, ?, ?, 'ABERTA', ?, ?, ?, ?, 0, ?, ?::jsonb, now(), now())
                """,
                windowId,
                eventId,
                roomId,
                Timestamp.from(Instant.parse("2026-09-14T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-14T10:05:00Z")),
                Timestamp.from(Instant.parse("2026-09-14T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-14T10:05:00Z")),
                "admin-window:" + windowId,
                "{\"origin\":\"admin-test\"}");
        jdbcTemplate.update("""
                insert into evento_janela_evidencia
                (id, evento_janela_avaliacao_id, medicao_id, papel, created_at)
                values (gen_random_uuid(), ?, ?, 'INICIO', now())
                """, windowId, measurementId);
        jdbcTemplate.update("""
                insert into evento_ocorrencia
                (id, evento_definicao_id, compartimento_id, sensor_external_id, status, inicio_em, detectado_em,
                 chave_idempotencia, contexto_snapshot, conteudo_fingerprint, created_at, updated_at)
                values (?, ?, ?, ?, 'DETECTADO', ?, ?, ?, ?::jsonb, ?, now(), now())
                """,
                occurrenceId,
                eventId,
                roomId,
                sensorId,
                Timestamp.from(Instant.parse("2026-09-14T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-14T10:00:00Z")),
                "admin-occurrence:" + occurrenceId,
                "{\"origin\":\"admin-test\"}",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        jdbcTemplate.update("""
                insert into evento_ocorrencia_evidencia
                (id, evento_ocorrencia_id, medicao_id, papel, created_at)
                values (gen_random_uuid(), ?, ?, 'CONDICAO', now())
                """, occurrenceId, measurementId);
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("""
                truncate table
                    xp_lancamento,
                    atividade_evento,
                    evento_ocorrencia_evidencia,
                    evento_ocorrencia,
                    evento_janela_evidencia,
                    evento_janela_avaliacao,
                    evento_avaliacao_request,
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
}
