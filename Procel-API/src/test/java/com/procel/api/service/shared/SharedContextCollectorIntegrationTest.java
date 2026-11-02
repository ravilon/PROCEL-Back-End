package com.procel.api.service.shared;

import com.procel.api.entity.missions.EventoCondicao;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.service.missions.rules.RoomStateFactLoader;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"procel.sensors.seed-path=missing-test-sensors.json","procel.security.bootstrap-admin.enabled=false","procel.shared-context.collector.enabled=false","procel.shared-context.collector.allow-loopback=true"})
@Testcontainers(disabledWithoutDocker=true)
class SharedContextCollectorIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16");
    static HttpServer server; static final AtomicReference<String> query=new AtomicReference<>();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("WEATHER_TEST_KEY",()->"super-secret-test-key");}
    @BeforeAll static void server() throws Exception {server=HttpServer.create(new InetSocketAddress(0),0);server.createContext("/current",exchange->{query.set(exchange.getRequestURI().getRawQuery());byte[] body="{\"current\":{\"temp_c\":18.75,\"last_updated_epoch\":1793577600}}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();}
    @AfterAll static void stop(){if(server!=null)server.stop(0);}
    @Autowired JdbcTemplate jdbc; @Autowired SharedContextCollector collector;
    @Autowired SharedContextService contexts; @Autowired RoomStateFactLoader factLoader;
    @Autowired ParametroDefRepository parameters;

    @Test void weatherApiQueryUsesBackendSecretCoordinatesEpochAndDeduplicates() {
        UUID context=UUID.randomUUID(),integration=UUID.randomUUID(),parameter=UUID.randomUUID();
        jdbc.update("insert into tipo_de_sensor(nome) values (?)","weather-e2e");
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,descricao,ativo) values (?,'NUMERIC','°C',?,'temperatura_externa','External',true)",parameter,"weather-e2e");
        jdbc.update("insert into campus(nome,latitude,longitude) values (?,?,?)","Campus E2E",-31.770000,-52.340000);
        jdbc.update("insert into unidade(nome) values (?)","Unit E2E");
        jdbc.update("insert into predio(id,campus_id,nome) values (?,?,?)","Building E2E","Campus E2E","Building E2E");
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values (?,?,?,?,?)","Room E2E","Building E2E","Unit E2E","Room E2E","sala");
        jdbc.update("insert into contexto_compartilhado(id,nome,location_mode,campus_nome) values (?,?,'CAMPUS',?)",context,"E2E","Campus E2E");
        jdbc.update("insert into contexto_compartilhado_compartimento(contexto_id,compartimento_id) values (?,?)",context,"Room E2E");
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) values (?,?,?,?,true)","weather-e2e","Weather E2E","weather-e2e",context);
        jdbc.update("insert into contexto_fonte_selecao(compartimento_id,parametro_def_id,sensor_external_id,validade_segundos) values (?,?,?,3600)","Room E2E",parameter,"weather-e2e");
        jdbc.update("""
            insert into contexto_integracao_externa(id,contexto_id,sensor_external_id,nome,endpoint_url,metodo,auth_mode,
              credential_env,credential_query_param,coordinate_query_param,temperature_json_pointer,observed_at_json_pointer,
              observed_at_format,interval_seconds,timeout_millis,max_attempts)
            values (?,?,?,?,?,'GET','QUERY','WEATHER_TEST_KEY','key','q','/current/temp_c','/current/last_updated_epoch','UNIX_SECONDS',60,3000,1)
            """,integration,context,"weather-e2e","WeatherAPI E2E","http://localhost:"+server.getAddress().getPort()+"/current");

        var tested=collector.testNow(integration);var first=collector.collectNow(integration);var repeated=collector.collectNow(integration);
        assertThat(tested.outcome()).isEqualTo("TEST_SUCCESS");assertThat(tested.persisted()).isFalse();
        assertThat(first.outcome()).isEqualTo("NEW_MEASUREMENT");assertThat(repeated.outcome()).isEqualTo("REPEATED_OBSERVATION");
        assertThat(first.observationAt()).isEqualTo(Instant.ofEpochSecond(1793577600));
        assertThat(query.get()).contains("key=super-secret-test-key").contains("q=-31.770000%2C-52.340000");
        Instant reference=first.observationAt().plusSeconds(60);
        assertThat(contexts.latest("Room E2E",parameter,reference).status()).isEqualTo("AVAILABLE");
        assertThat(contexts.latest("Room E2E",parameter,first.observationAt().minusSeconds(1)).status()).isEqualTo("MISSING");
        assertThat(contexts.latest("Room E2E",parameter,first.observationAt().plusSeconds(3601)).status()).isEqualTo("STALE");
        EventoCondicao condition=new EventoCondicao();condition.setParametroDef(parameters.findById(parameter).orElseThrow());
        EventoDefinicao event=new EventoDefinicao();event.setCondicoes(java.util.List.of(condition));event.setLacunaMaximaSegundos(3600);
        assertThat(factLoader.load(event,"Room E2E",reference,null).measurements()).hasSize(1);
        assertThat(factLoader.load(event,"Room E2E",first.observationAt().minusSeconds(1),null).measurements()).isEmpty();
        assertThat(factLoader.load(event,"Room E2E",first.observationAt().plusSeconds(3601),null).measurements()).isEmpty();
        jdbc.update("update campus set latitude=-30.000000,longitude=-51.000000 where nome='Campus E2E'");
        assertThat(contexts.latest("Room E2E",parameter,reference).status()).isEqualTo("MISSING");
        assertThat(factLoader.load(event,"Room E2E",reference,null).measurements()).isEmpty();
        assertThat(collector.collectNow(integration).outcome()).isEqualTo("NEW_MEASUREMENT");
        assertThat(contexts.latest("Room E2E",parameter,reference).status()).isEqualTo("AVAILABLE");
        assertThat(factLoader.load(event,"Room E2E",reference,null).measurements()).hasSize(1);
        assertThat(query.get()).contains("q=-30.000000%2C-51.000000");
        assertThat(jdbc.queryForObject("select count(*) from medicao where sensor_external_id='weather-e2e'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from contexto_coleta_execucao where integracao_id=?",Integer.class,integration)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(distinct location_hash) from contexto_coleta_execucao where integracao_id=?",Integer.class,integration)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from evento_avaliacao_request where medicao_id in (select id from medicao where sensor_external_id='weather-e2e')",Integer.class)).isZero();
        assertThat(contexts.average7d("Room E2E",parameter,first.observationAt()).status()).isEqualTo("INSUFFICIENT_COVERAGE");
        String hash=jdbc.queryForObject("select location_hash from contexto_coleta_execucao where integracao_id=? order by finished_at desc limit 1",String.class,integration);
        for(int i=121;i>=1;i--){if(i==61)continue;UUID measurement=UUID.randomUUID();Instant at=first.observationAt().minus(i,ChronoUnit.HOURS);jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,?,?,?)",measurement,"weather-e2e",java.sql.Timestamp.from(at),java.sql.Timestamp.from(at),"API");jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,numeric_value) values (?,?,?,?)",UUID.randomUUID(),measurement,parameter,i>61?10:20);jdbc.update("insert into contexto_coleta_execucao(id,integracao_id,started_at,finished_at,outcome,observation_at,medicao_id,latitude,longitude,location_source,campus_nome,location_hash) values (?,?,now(),now(),'NEW_MEASUREMENT',?,?,?,?,'CAMPUS','Campus E2E',?)",UUID.randomUUID(),integration,java.sql.Timestamp.from(at),measurement,-30.000000,-51.000000,hash);}
        var average=contexts.average7d("Room E2E",parameter,first.observationAt());
        assertThat(average.status()).isEqualTo("AVAILABLE");assertThat(average.coveredHours()).isEqualByComparingTo("120.00");assertThat(average.average()).isEqualByComparingTo("15.00");assertThat(average.distinctObservations()).isEqualTo(121);
    }
}
