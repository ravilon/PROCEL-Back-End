package com.proben.api.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class EnvironmentRuleMigrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void upgradesV32AndEnforcesScopeAndDefaultUniqueness() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target("32").load().migrate();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("insert into regra_ambiente(nome,categoria,padrao,escopo,timezone,cron_expression,politica_ocupacao,politica_beneficiario,created_by,updated_by) values ('Global','ILUMINACAO',true,'GLOBAL','UTC','0 */10 * * * *','IGNORE','NONE','test','test')");
            assertThatThrownBy(() -> statement.executeUpdate("insert into regra_ambiente(nome,categoria,padrao,escopo,timezone,cron_expression,politica_ocupacao,politica_beneficiario,created_by,updated_by) values ('Global 2','ILUMINACAO',true,'GLOBAL','UTC','0 */10 * * * *','IGNORE','NONE','test','test')"))
                    .hasMessageContaining("ux_regra_ambiente_global_default_active");
            assertThatThrownBy(() -> statement.executeUpdate("insert into regra_ambiente(nome,categoria,padrao,escopo,timezone,cron_expression,politica_ocupacao,politica_beneficiario,created_by,updated_by) values ('Invalid','EQUIPAMENTOS',false,'GLOBAL','UTC','0 */10 * * * *','IGNORE','NONE','test','test')"))
                    .hasMessageContaining("ck_regra_ambiente_scope_fields");
            try (var result = statement.executeQuery("select count(*) from flyway_schema_history where version='33' and success")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }
}
