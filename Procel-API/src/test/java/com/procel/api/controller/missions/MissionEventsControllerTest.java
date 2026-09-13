package com.procel.api.controller.missions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.entity.missions.Missao;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.TipoDeSensor;
import com.procel.api.repository.missions.MissaoRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.repository.sensors.TipoDeSensorRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MissionEventsControllerTest {

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
    @Autowired ObjectMapper objectMapper;
    @Autowired MissaoRepository missaoRepo;
    @Autowired TipoDeSensorRepository tipoRepo;
    @Autowired ParametroDefRepository parametroRepo;
    @Autowired JdbcTemplate jdbcTemplate;

    private Missao missao;
    private ParametroDef numericParam;
    private ParametroDef booleanParam;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        missao = missaoRepo.save(new Missao("Missao " + suffix, "Descricao", "Individual", 10, true));
        TipoDeSensor tipo = tipoRepo.save(new TipoDeSensor("EVENT-TYPE-" + suffix));
        numericParam = parametroRepo.save(new ParametroDef(
                tipo,
                "temperature_" + suffix,
                "Temperatura",
                DataType.NUMERIC,
                "C"
        ));
        booleanParam = parametroRepo.save(new ParametroDef(
                tipo,
                "presence_" + suffix,
                "Presenca",
                DataType.BOOLEAN,
                null
        ));
    }

    @Test
    void adminPersistsAndReadsFullEventAggregate() throws Exception {
        String event = mvc.perform(post("/api/missions/" + missao.getId() + "/events")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(eventBody("Evento principal", true, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.missaoId").value(missao.getId().toString()))
                .andExpect(jsonPath("$.quantidadeNecessaria").value(2))
                .andReturn().getResponse().getContentAsString();
        String eventId = objectMapper.readTree(event).get("id").asText();

        mvc.perform(post("/api/mission-events/" + eventId + "/conditions")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(numericConditionBody(numericParam.getId(), 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parametroDefId").value(numericParam.getId().toString()))
                .andExpect(jsonPath("$.ordem").value(1));

        mvc.perform(post("/api/mission-events/" + eventId + "/conditions")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(booleanConditionBody(booleanParam.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorBoolean").value(true));

        mvc.perform(get("/api/mission-events/" + eventId)
                        .with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.condicoes[0].ordem").value(1))
                .andExpect(jsonPath("$.condicoes[0].parametroDefId").value(numericParam.getId().toString()))
                .andExpect(jsonPath("$.condicoes[1].ordem").value(2))
                .andExpect(jsonPath("$.condicoes[1].parametroDefId").value(booleanParam.getId().toString()));
    }

    @Test
    void supportsUpdateAndLogicalDelete() throws Exception {
        String event = mvc.perform(post("/api/missions/" + missao.getId() + "/events")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(eventBody("Evento", true, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String eventId = objectMapper.readTree(event).get("id").asText();

        mvc.perform(put("/api/mission-events/" + eventId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(eventBody("Evento atualizado", true, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Evento atualizado"))
                .andExpect(jsonPath("$.quantidadeNecessaria").value(3));

        mvc.perform(delete("/api/mission-events/" + eventId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/mission-events/" + eventId)
                        .with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void enforcesRoleAuthorization() throws Exception {
        mvc.perform(post("/api/missions/" + missao.getId() + "/events")
                        .with(user("operador").roles("OPERADOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(eventBody("Evento", true, 1)))
                .andExpect(status().isForbidden());

        String event = mvc.perform(post("/api/missions/" + missao.getId() + "/events")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(eventBody("Evento", true, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String eventId = objectMapper.readTree(event).get("id").asText();

        mvc.perform(get("/api/mission-events/" + eventId)
                        .with(user("analista").roles("ANALISTA")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/mission-events/" + eventId)
                        .with(user("usuario").roles("USUARIO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsActivationForInactiveMission() throws Exception {
        Missao inactive = missaoRepo.save(new Missao("Inativa", null, "Individual", 0, false));

        mvc.perform(post("/api/missions/" + inactive.getId() + "/events")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(eventBody("Evento ativo", true, 1)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/missions/" + inactive.getId() + "/events")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(eventBody("Evento inativo", false, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void databaseForeignKeysProtectMissionAndParameterReferences() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (gen_random_uuid(), ?, 'bad', 'MEDICAO_RECEBIDA', 'INSTANTANEO', 'ALL',
                        'SEM_ATRIBUICAO_AUTOMATICA', 1, 1, true, now(), now())
                """, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);

        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into evento_definicao
                (id, missao_id, nome, tipo_disparo, modo_avaliacao, operador_logico, politica_atribuicao,
                 quantidade_necessaria, ordem, ativo, created_at, updated_at)
                values (?, ?, 'ok', 'MEDICAO_RECEBIDA', 'INSTANTANEO', 'ALL',
                        'SEM_ATRIBUICAO_AUTOMATICA', 1, 1, true, now(), now())
                """, eventId, missao.getId());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into evento_condicao
                (id, evento_definicao_id, parametro_def_id, operador, valor_numeric_1, agregacao,
                 obrigatoria, ordem, ativo, created_at)
                values (gen_random_uuid(), ?, ?, 'GT', 10, 'ULTIMO', true, 1, true, now())
                """, eventId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);
    }

    private static String eventBody(String nome, boolean ativo, int quantidadeNecessaria) {
        return """
                {
                  "nome":"%s",
                  "descricao":"Descricao",
                  "tipoDisparo":"MEDICAO_RECEBIDA",
                  "modoAvaliacao":"INSTANTANEO",
                  "operadorLogico":"ALL",
                  "politicaAtribuicao":"SEM_ATRIBUICAO_AUTOMATICA",
                  "janelaSegundos":60,
                  "quantidadeNecessaria":%d,
                  "cooldownSegundos":30,
                  "ordem":1,
                  "ativo":%s
                }
                """.formatted(nome, quantidadeNecessaria, ativo);
    }

    private static String numericConditionBody(UUID parametroDefId, int ordem) {
        return """
                {
                  "parametroDefId":"%s",
                  "operador":"GT",
                  "valorNumeric1":30,
                  "agregacao":"ULTIMO",
                  "obrigatoria":true,
                  "ordem":%d
                }
                """.formatted(parametroDefId, ordem);
    }

    private static String booleanConditionBody(UUID parametroDefId, int ordem) {
        return """
                {
                  "parametroDefId":"%s",
                  "operador":"EQ",
                  "valorBoolean":true,
                  "agregacao":"ULTIMO",
                  "obrigatoria":true,
                  "ordem":%d
                }
                """.formatted(parametroDefId, ordem);
    }
}
