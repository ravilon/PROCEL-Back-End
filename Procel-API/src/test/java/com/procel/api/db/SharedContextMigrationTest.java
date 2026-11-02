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
class SharedContextMigrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16");

    @Test void createsSharedScopeAndEnforcesExactlyOneSensorOwner() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());var s=c.createStatement()){
            UUID context=UUID.randomUUID();
            s.executeUpdate("insert into tipo_de_sensor(nome) values ('weather-test')");
            s.executeUpdate("insert into contexto_compartilhado(id,nome) values ('"+context+"','Campus teste')");
            s.executeUpdate("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) values ('shared-weather','Weather','weather-test','"+context+"',true)");
            try(var rs=s.executeQuery("select count(*) from sensor where external_id='shared-weather' and compartimento_id is null")){
                assertThat(rs.next()).isTrue(); assertThat(rs.getInt(1)).isEqualTo(1);
            }
            assertThatThrownBy(()->s.executeUpdate("insert into sensor(external_id,nome,tipo_nome,ativo) values ('orphan','Orphan','weather-test',true)"))
                    .hasMessageContaining("ck_sensor_exactly_one_scope");
        }
    }
}
