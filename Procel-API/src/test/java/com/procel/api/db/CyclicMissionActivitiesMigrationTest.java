package com.procel.api.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CyclicMissionActivitiesMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void v22MigratesExistingActivitiesToUniqueCycle() throws Exception {
        String dbName = "v22_" + UUID.randomUUID().toString().replace("-", "");
        postgres.execInContainer("createdb", "-U", postgres.getUsername(), dbName);
        String url = postgres.getJdbcUrl().replace(postgres.getDatabaseName(), dbName);

        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("21")
                .load()
                .migrate();

        UUID missaoId = UUID.randomUUID();
        UUID atividadeConcluidaId = UUID.randomUUID();
        UUID atividadePendenteId = UUID.randomUUID();
        seedHistoricalActivities(url, missaoId, atividadeConcluidaId, atividadePendenteId);

        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.prepareStatement("""
                     select chave_ciclo, ciclo_tipo, progresso_atual, progresso_necessario, conclusao_automatica
                     from atividade
                     where id = ?
                     """)) {
            statement.setObject(1, atividadeConcluidaId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("chave_ciclo")).isEqualTo("UNICA");
                assertThat(result.getString("ciclo_tipo")).isEqualTo("UNICA");
                assertThat(result.getInt("progresso_atual")).isEqualTo(1);
                assertThat(result.getInt("progresso_necessario")).isEqualTo(1);
                assertThat(result.getBoolean("conclusao_automatica")).isTrue();
            }
        }

        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.prepareStatement("""
                     select progresso_atual
                     from atividade
                     where id = ?
                     """)) {
            statement.setObject(1, atividadePendenteId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("progresso_atual")).isZero();
            }
        }

        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var result = connection.createStatement().executeQuery("""
                     select count(*)
                     from pg_indexes
                     where schemaname = 'public'
                       and indexname = 'uk_atividade_pessoa_missao_ciclo'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    private static void seedHistoricalActivities(
            String url,
            UUID missaoId,
            UUID atividadeConcluidaId,
            UUID atividadePendenteId
    ) throws Exception {
        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("""
                    insert into pessoa (id, nome, email, password, created_at)
                    values ('p1', 'Pessoa 1', 'p1@example.com', 'hash', now()),
                           ('p2', 'Pessoa 2', 'p2@example.com', 'hash', now())
                    """);
            statement.execute("""
                    insert into missao (id, titulo, descricao, tipo, value, ativo, created_at)
                    values ('%s', 'Missao historica', 'Descricao', 'Individual', 10, true, now())
                    """.formatted(missaoId));
            statement.execute("""
                    insert into atividade
                    (id, pessoa_id, missao_id, status, assigned_at, started_at, completed_at)
                    values
                    ('%s', 'p1', '%s', 'CONCLUIDA', now(), now(), now()),
                    ('%s', 'p2', '%s', 'PENDENTE', now(), null, null)
                    """.formatted(atividadeConcluidaId, missaoId, atividadePendenteId, missaoId));
        }
    }
}
