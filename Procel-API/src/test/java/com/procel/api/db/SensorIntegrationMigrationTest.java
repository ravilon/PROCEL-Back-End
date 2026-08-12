package com.procel.api.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class SensorIntegrationMigrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void migratesEmptyDatabaseThroughV16() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var result = connection.createStatement().executeQuery("""
                     select count(*)
                     from information_schema.tables
                     where table_name in (
                         'sensor_integration_profile',
                         'sensor_integration_parser_version',
                         'sensor_integration_value_mapping',
                         'sensor_integration_binding'
                     )
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(4);
        }
    }

    @Test
    void migratesDatabaseFromV14PreservingDirectMetadataWithNullIntegrationContext() throws Exception {
        String dbName = "v14_" + UUID.randomUUID().toString().replace("-", "");
        postgres.execInContainer("createdb", "-U", postgres.getUsername(), dbName);
        String url = postgres.getJdbcUrl().replace(postgres.getDatabaseName(), dbName);

        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("14")
                .load()
                .migrate();

        seedDirectMetadata(url);

        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var result = connection.createStatement().executeQuery("""
                     select integration_profile_id, parser_version_id
                     from medicao_ingestao_metadata
                     where message_id = 'historical-msg'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getObject(1)).isNull();
            assertThat(result.getObject(2)).isNull();
        }
    }

    @Test
    void v16AbortsWhenHistoricalDirectDuplicatesExist() throws Exception {
        String dbName = "dups_" + UUID.randomUUID().toString().replace("-", "");
        postgres.execInContainer("createdb", "-U", postgres.getUsername(), dbName);
        String url = postgres.getJdbcUrl().replace(postgres.getDatabaseName(), dbName);

        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("15")
                .load()
                .migrate();

        seedDirectMetadata(url);
        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("alter table medicao_ingestao_metadata drop constraint ux_medicao_ingestao_producer_sensor_message");
            statement.execute("""
                    insert into medicao_ingestao_metadata
                    (producer_id, sensor_external_id, message_id, source, api_received_at, payload_fingerprint, status)
                    values ('producer', 'SII-MIGRATION', 'historical-msg', 'API', now(), repeat('b', 64), 'PROCESSING')
                    """);
        }

        assertThatThrownBy(() -> Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate())
                .hasMessageContaining("duplicate direct ingestion keys");
    }

    private void seedDirectMetadata(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("insert into campus (nome) values ('Campus migration')");
            statement.execute("insert into unidade (nome) values ('Unidade migration')");
            statement.execute("""
                    insert into predio (id, campus_id, nome)
                    select 'PREDIO-MIGRATION', nome, 'Predio migration' from campus where nome = 'Campus migration'
                    """);
            statement.execute("""
                    insert into compartimento (id, predio_id, unidade_id, nome, tipo)
                    select 'ROOM-MIGRATION', p.id, u.nome, 'Sala migration', 'Sala'
                    from predio p cross join unidade u
                    where p.nome = 'Predio migration' and u.nome = 'Unidade migration'
                    """);
            statement.execute("insert into tipo_de_sensor (nome) values ('TYPE-MIGRATION')");
            statement.execute("""
                    insert into sensor (external_id, nome, tipo_nome, compartimento_id, ativo)
                    values ('SII-MIGRATION', 'Sensor migration', 'TYPE-MIGRATION', 'ROOM-MIGRATION', true)
                    """);
            statement.execute("""
                    insert into medicao_ingestao_metadata
                    (producer_id, sensor_external_id, message_id, source, api_received_at, payload_fingerprint, status)
                    values ('producer', 'SII-MIGRATION', 'historical-msg', 'API', now(), repeat('a', 64), 'PROCESSING')
                    """);
        }
    }
}
