package com.proben.api.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SharedContextMigrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16");

    @Test @Order(2) void createsSharedScopeAndEnforcesExactlyOneSensorOwner() throws Exception {
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

    @Test @Order(1) void upgradesAConfigurationFromV31ToV32WithoutLosingItsMappingOrQuery() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .locations("classpath:db/migration").target("31").load().migrate();
        UUID context=UUID.randomUUID();
        UUID parameter=UUID.randomUUID();
        UUID integration=UUID.randomUUID();
        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());var s=c.createStatement()){
            s.executeUpdate("insert into tipo_de_sensor(nome) values ('weather-upgrade')");
            s.executeUpdate("insert into parametro_def(id,tipo_nome,nome,data_type,numeric_unit,ativo) values ('"+parameter+"','weather-upgrade','temperatura_externa','NUMERIC','°C',true)");
            s.executeUpdate("insert into contexto_compartilhado(id,nome,latitude,longitude) values ('"+context+"','Contexto legado',-31.765, -52.337)");
            s.executeUpdate("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) values ('weather-upgrade-source','Weather legado','weather-upgrade','"+context+"',true)");
            s.executeUpdate("insert into contexto_integracao_externa(id,contexto_id,sensor_external_id,nome,endpoint_url,temperature_json_pointer,coordinate_query_param) values ('"+integration+"','"+context+"','weather-upgrade-source','Coleta legada','https://example.com/weather','/current/temp_c','q')");
        }

        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();

        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
            var statement=c.prepareStatement("select mappings_json,query_params_json,interval_seconds from contexto_integracao_externa where id=?")){
            statement.setObject(1,integration);
            try(var result=statement.executeQuery()){
                assertThat(result.next()).isTrue();
                assertThat(result.getString("mappings_json")).contains(parameter.toString()).contains("/current/temp_c").contains("temperatura_externa");
                assertThat(result.getString("query_params_json")).contains("\"q\"").contains("{{context.coordinates}}");
                assertThat(result.getInt("interval_seconds")).isEqualTo(900);
            }
        }
    }
}
