package com.procel.api.controller.people;

import com.procel.api.entity.missions.AtividadeStatus;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PessoaXpControllerTest {

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

    String pessoaId;
    UUID atividadeId;
    UUID missaoId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        pessoaId = "user-xp";
        missaoId = UUID.randomUUID();
        atividadeId = UUID.randomUUID();
        seedPessoa(pessoaId);
        seedPessoa("other-xp");
        seedMissao();
        seedActivity(atividadeId, pessoaId, "Missao XP");
        insertLedger(UUID.fromString("00000000-0000-0000-0000-000000000001"), "CONCESSAO", 10, Instant.parse("2026-09-13T10:00:00Z"));
        insertLedger(UUID.fromString("00000000-0000-0000-0000-000000000002"), "AJUSTE", 2, Instant.parse("2026-09-13T11:00:00Z"));
    }

    @Test
    void userCanReadOwnBalanceAndStatement() throws Exception {
        mvc.perform(get("/api/pessoas/{pessoaId}/xp", pessoaId)
                        .with(user(pessoaId).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pessoaId").value(pessoaId))
                .andExpect(jsonPath("$.saldoTotal").value(12))
                .andExpect(jsonPath("$.totalConcedido").value(10))
                .andExpect(jsonPath("$.totalAjustado").value(2));

        mvc.perform(get("/api/pessoas/{pessoaId}/xp/lancamentos?tipo=CONCESSAO&page=0&size=10", pessoaId)
                        .with(user(pessoaId).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipo").value("CONCESSAO"))
                .andExpect(jsonPath("$.content[0].atividadeId").value(atividadeId.toString()))
                .andExpect(jsonPath("$.content[0].missaoId").value(missaoId.toString()))
                .andExpect(jsonPath("$.content[0].missaoTitulo").value("Missao XP"));
    }

    @Test
    void userCannotReadAnotherPersonXp() throws Exception {
        mvc.perform(get("/api/pessoas/{pessoaId}/xp", pessoaId)
                        .with(user("other-xp").roles("USUARIO")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/pessoas/{pessoaId}/xp/lancamentos", pessoaId)
                        .with(user("other-xp").roles("USUARIO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAndOperatorCanReadThirdPartyXp() throws Exception {
        mvc.perform(get("/api/pessoas/{pessoaId}/xp", pessoaId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoTotal").value(12));

        mvc.perform(get("/api/pessoas/{pessoaId}/xp/lancamentos", pessoaId)
                        .with(user("operador").roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private void seedPessoa(String id) {
        jdbcTemplate.update("""
                insert into pessoa (id, nome, email, password, created_at)
                values (?, ?, ?, 'hash', now())
                """, id, id, id + "@example.com");
    }

    private void seedMissao() {
        jdbcTemplate.update("""
                insert into missao (id, titulo, descricao, tipo, value, ativo, created_at)
                values (?, 'Missao XP', 'Descricao', 'Individual', 10, true, now())
                """, missaoId);
    }

    private void seedActivity(UUID id, String pessoa, String title) {
        jdbcTemplate.update("""
                insert into atividade
                (id, pessoa_id, missao_id, status, assigned_at, completed_at, chave_ciclo, ciclo_tipo,
                 progresso_atual, progresso_necessario, conclusao_automatica)
                values (?, ?, ?, ?, now(), now(), 'UNICA', 'UNICA', 1, 1, true)
                """, id, pessoa, missaoId, AtividadeStatus.CONCLUIDA.name());
    }

    private void insertLedger(UUID id, String tipo, int quantidade, Instant createdAt) {
        jdbcTemplate.update("""
                insert into xp_lancamento
                (id, pessoa_id, atividade_id, missao_id, tipo, quantidade, chave_idempotencia, descricao, created_at, created_by)
                values (?, ?, ?, ?, ?, ?, ?, 'Teste', ?, 'TEST')
                """,
                id,
                pessoaId,
                atividadeId,
                missaoId,
                tipo,
                quantidade,
                "controller:" + id,
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
}
