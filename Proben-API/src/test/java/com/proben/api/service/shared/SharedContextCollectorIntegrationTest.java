package com.proben.api.service.shared;

import com.proben.api.entity.missions.EventoCondicao;
import com.proben.api.entity.missions.EventoDefinicao;
import com.proben.api.dto.shared.SharedContextDTOs.IntegrationRequest;
import com.proben.api.dto.shared.SharedContextDTOs.HttpMappingRequest;
import com.proben.api.repository.sensors.ParametroDefRepository;
import com.proben.api.service.missions.rules.RoomStateFactLoader;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"proben.sensors.seed-path=missing-test-sensors.json","proben.security.bootstrap-admin.enabled=false","proben.shared-context.collector.enabled=false","proben.shared-context.collector.allow-loopback=true"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker=true)
class SharedContextCollectorIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16");
    static HttpServer server; static final AtomicReference<String> query=new AtomicReference<>();
    static final AtomicReference<String> authorization=new AtomicReference<>(), apiKeyHeader=new AtomicReference<>(), requestBody=new AtomicReference<>();
    static final AtomicInteger requests=new AtomicInteger();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("PROBEN_CREDENTIAL_ENCRYPTION_KEY",()->"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");}
    @BeforeAll static void server() throws Exception {server=HttpServer.create(new InetSocketAddress(0),0);server.createContext("/current",exchange->{requests.incrementAndGet();query.set(exchange.getRequestURI().getRawQuery());authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));apiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] body="{\"current\":{\"temp_c\":18.75,\"humidity\":64,\"last_updated_epoch\":1793577600}}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();}
    @AfterAll static void stop(){if(server!=null)server.stop(0);}
    @Autowired JdbcTemplate jdbc; @Autowired SharedContextCollector collector;
    @Autowired SharedContextService contexts; @Autowired RoomStateFactLoader factLoader;
    @Autowired ParametroDefRepository parameters;
    @Autowired MockMvc mvc;

    @Test void weatherApiQueryUsesBackendSecretCoordinatesEpochAndDeduplicates() throws Exception {
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
              credential_query_param,coordinate_query_param,temperature_json_pointer,observed_at_json_pointer,
              observed_at_format,interval_seconds,timeout_millis,max_attempts)
            values (?,?,?,?,?,'GET','QUERY','key','q','/current/temp_c','/current/last_updated_epoch','UNIX_SECONDS',60,3000,1)
            """,integration,context,"weather-e2e","WeatherAPI E2E","http://localhost:"+server.getAddress().getPort()+"/current");
        contexts.configureIntegration(context,request("initial-managed-secret"));

        var tested=collector.testNow(integration);var first=collector.collectNow(integration);var repeated=collector.collectNow(integration);
        assertThat(tested.outcome()).isEqualTo("TEST_SUCCESS");assertThat(tested.persisted()).isFalse();
        assertThat(first.outcome()).isEqualTo("NEW_MEASUREMENT");assertThat(repeated.outcome()).isEqualTo("REPEATED_OBSERVATION");
        assertThat(first.observationAt()).isEqualTo(Instant.ofEpochSecond(1793577600));
        assertThat(query.get()).contains("key=initial-managed-secret").contains("q=-31.770000%2C-52.340000");
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

        var managed=contexts.configureIntegration(context,request("managed-provider-secret"));
        assertThat(managed.credentialConfigured()).isTrue();
        assertThat(managed.toString()).doesNotContain("managed-provider-secret");
        byte[] encrypted=jdbc.queryForObject("select credential_ciphertext from contexto_integracao_externa where id=?",byte[].class,integration);
        assertThat(new String(encrypted,StandardCharsets.UTF_8)).doesNotContain("managed-provider-secret");
        collector.testNow(integration);assertThat(query.get()).contains("key=managed-provider-secret");
        contexts.configureIntegration(context,request(null));collector.testNow(integration);assertThat(query.get()).contains("key=managed-provider-secret");
        int beforeInvalidCredential=requests.get();jdbc.update("update contexto_integracao_externa set credential_ciphertext=set_byte(credential_ciphertext,0,(get_byte(credential_ciphertext,0)+1)%256) where id=?",integration);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->collector.testNow(integration)).hasMessageContaining("cannot be decrypted");assertThat(requests.get()).isEqualTo(beforeInvalidCredential);
        contexts.configureIntegration(context,request("replacement-secret"));collector.testNow(integration);assertThat(query.get()).contains("key=replacement-secret");
        var transientResult=collector.testConfiguration(context,request("transient-secret"));assertThat(transientResult.persisted()).isFalse();assertThat(query.get()).contains("key=transient-secret");
        assertThat(transientResult.httpStatus()).isEqualTo(200);assertThat(transientResult.durationMillis()).isNotNegative();
        assertThat(transientResult.method()).isEqualTo("GET");assertThat(transientResult.sanitizedUrl()).doesNotContain("transient-secret").doesNotContain("key=");
        assertThat(transientResult.queryParameters()).containsEntry("key","[REDACTED]");assertThat(transientResult.rawJson()).contains("temp_c").doesNotContain("transient-secret");
        assertThat(transientResult.mappings()).hasSize(1).allSatisfy(mapping->assertThat(mapping.success()).isTrue());
        assertThat(transientResult.observedAtOriginal()).isEqualTo("1793577600");assertThat(transientResult.observedAtFormat()).isEqualTo("UNIX_SECONDS");
        assertThat(transientResult.toString()).doesNotContain("transient-secret");
        assertThat(jdbc.queryForObject("select count(*) from contexto_coleta_execucao where integracao_id=? and error_message like '%secret%'",Integer.class,integration)).isZero();
        mvc.perform(get("/api/shared-contexts/"+context+"/integrations").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(result->assertThat(result.getResponse().getContentAsString()).doesNotContain("replacement-secret").doesNotContain("managed-provider-secret"));
        mvc.perform(delete("/api/shared-contexts/integrations/"+integration+"/credential").with(user("operator").roles("OPERADOR")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/shared-contexts/integrations/"+integration+"/credential").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        assertThat(contexts.integrations(context).getFirst().credentialConfigured()).isFalse();int beforeMissing=requests.get();org.assertj.core.api.Assertions.assertThatThrownBy(()->collector.testNow(integration)).hasMessageContaining("credential is absent");assertThat(requests.get()).isEqualTo(beforeMissing);
    }

    @Test void genericPostSupportsTemplatesHeaderBearerAndMultipleMappings() {
        String suffix=UUID.randomUUID().toString();UUID context=UUID.randomUUID(),temperature=UUID.randomUUID(),humidity=UUID.randomUUID();
        String type="generic-"+suffix,sensor="sensor-"+suffix;
        jdbc.update("insert into tipo_de_sensor(nome) values (?)",type);
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,descricao,ativo) values (?,'NUMERIC','C',?,'temperature','Temperature',true)",temperature,type);
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,descricao,ativo) values (?,'NUMERIC','%',?,'humidity','Humidity',true)",humidity,type);
        jdbc.update("insert into contexto_compartilhado(id,nome,location_mode,latitude,longitude) values (?,?,'OWN',?,?)",context,"Generic "+suffix,-31.77,-52.34);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) values (?,?,?,?,true)",sensor,"Generic",type,context);
        var mappings=java.util.List.of(
                new HttpMappingRequest(temperature,"temperature","/current/temp_c","NUMERIC","C","C",true,"NONE"),
                new HttpMappingRequest(humidity,"humidity","/current/humidity","NUMERIC","%","%",true,"NONE"));
        IntegrationRequest headerRequest=genericRequest(sensor,"HEADER","header-secret","X-API-Key",null,mappings);
        var preview=collector.testConfiguration(context,headerRequest);
        assertThat(preview.persisted()).isFalse();assertThat(apiKeyHeader.get()).isEqualTo("header-secret");
        assertThat(query.get()).contains("coordinates=-31.770000%2C-52.340000");
        assertThat(requestBody.get()).contains("\"at\":").contains("-31.770000,-52.340000");
        var invalidMappings=java.util.List.of(new HttpMappingRequest(temperature,"temperature","/missing/value","NUMERIC","C","C",true,"NONE"));
        var invalidPreview=collector.testConfiguration(context,genericRequest(sensor,"HEADER","header-secret", "X-API-Key",null,invalidMappings));
        assertThat(invalidPreview.outcome()).isEqualTo("TEST_FAILED");assertThat(invalidPreview.persisted()).isFalse();
        assertThat(invalidPreview.errorType()).isEqualTo("POINTER_ERROR");assertThat(invalidPreview.mappings()).singleElement().satisfies(mapping->{assertThat(mapping.success()).isFalse();assertThat(mapping.error()).contains("JSON Pointer");});
        assertThat(invalidPreview.toString()).doesNotContain("header-secret");
        var invalidConversion=java.util.List.of(new HttpMappingRequest(temperature,"temperature","/current/temp_c","NUMERIC","C","C",true,"INVALID"));
        var conversionPreview=collector.testConfiguration(context,genericRequest(sensor,"HEADER","header-secret","X-API-Key",null,invalidConversion));
        assertThat(conversionPreview.errorType()).isEqualTo("CONVERSION_ERROR");assertThat(conversionPreview.mappings()).singleElement().satisfies(mapping->assertThat(mapping.error()).contains("unsupported conversion"));

        IntegrationRequest bearerRequest=genericRequest(sensor,"BEARER","bearer-secret",null,null,mappings);
        var configured=contexts.configureIntegration(context,bearerRequest);
        assertThat(configured.requestBody()).contains("context.coordinates");
        var collected=collector.collectNow(configured.id());
        assertThat(collected.outcome()).isEqualTo("NEW_MEASUREMENT");
        assertThat(authorization.get()).isEqualTo("Bearer bearer-secret");
        assertThat(jdbc.queryForObject("select count(*) from parametro_valor pv join medicao m on m.id=pv.medicao_id where m.sensor_external_id=?",Integer.class,sensor)).isEqualTo(2);
    }

    @Test void contextDeletionRemovesOnlySimpleLinksAndRejectsHistoricalDependencies() throws Exception {
        String suffix=UUID.randomUUID().toString();String type="delete-"+suffix,sensor="delete-sensor-"+suffix;
        String campus="Delete campus "+suffix,unit="Delete unit "+suffix,building="Delete building "+suffix,room="Delete room "+suffix;
        UUID empty=UUID.randomUUID(),used=UUID.randomUUID(),parameter=UUID.randomUUID(),integration=UUID.randomUUID(),measurement=UUID.randomUUID();
        jdbc.update("insert into tipo_de_sensor(nome) values (?)",type);
        jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,descricao,ativo) values (?,'NUMERIC','C',?,'temperature','Temperature',true)",parameter,type);
        jdbc.update("insert into campus(nome) values (?)",campus);
        jdbc.update("insert into unidade(nome) values (?)",unit);
        jdbc.update("insert into predio(id,campus_id,nome) values (?,?,?)",building,campus,building);
        jdbc.update("insert into compartimento(id,predio_id,unidade_id,nome,tipo) values (?,?,?,?,?)",room,building,unit,room,"sala");
        jdbc.update("insert into contexto_compartilhado(id,nome,location_mode,latitude,longitude) values (?,?,'OWN',?,?)",empty,"Empty "+suffix,-31.77,-52.34);
        jdbc.update("insert into contexto_compartilhado_compartimento(contexto_id,compartimento_id) values (?,?)",empty,room);
        mvc.perform(delete("/api/shared-contexts/"+empty).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from contexto_compartilhado where id=?",Integer.class,empty)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from contexto_compartilhado_compartimento where contexto_id=?",Integer.class,empty)).isZero();

        jdbc.update("insert into contexto_compartilhado(id,nome,location_mode,latitude,longitude) values (?,?,'OWN',?,?)",used,"Used "+suffix,-31.77,-52.34);
        jdbc.update("insert into contexto_compartilhado_compartimento(contexto_id,compartimento_id) values (?,?)",used,room);
        jdbc.update("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) values (?,?,?,?,true)",sensor,"Delete sensor",type,used);
        jdbc.update("insert into contexto_fonte_selecao(compartimento_id,parametro_def_id,sensor_external_id,validade_segundos) values (?,?,?,3600)",room,parameter,sensor);
        jdbc.update("insert into contexto_integracao_externa(id,contexto_id,sensor_external_id,nome,endpoint_url,temperature_json_pointer) values (?,?,?,?,?,?)",integration,used,sensor,"Delete integration","https://example.com/weather","/temperature");
        jdbc.update("insert into medicao(id,sensor_external_id,timestamp,recebido_em,source) values (?,?,now(),now(),'API')",measurement,sensor);
        jdbc.update("insert into parametro_valor(id,medicao_id,parametro_def_id,numeric_value) values (?,?,?,?)",UUID.randomUUID(),measurement,parameter,20);
        jdbc.update("insert into contexto_coleta_execucao(id,integracao_id,started_at,finished_at,outcome,observation_at,medicao_id,latitude,longitude,location_source,location_hash) values (?,?,now(),now(),'NEW_MEASUREMENT',now(),?,?,?,'OWN',?)",UUID.randomUUID(),integration,measurement,-31.77,-52.34,"location-hash");
        var preview=contexts.deletePreview(used);
        assertThat(preview.sensores()).isEqualTo(1);assertThat(preview.integracoes()).isEqualTo(1);
        assertThat(preview.fontesSelecionadas()).isEqualTo(1);assertThat(preview.medicoes()).isEqualTo(1);assertThat(preview.auditorias()).isEqualTo(1);
        assertThat(preview.deletable()).isFalse();
        mvc.perform(delete("/api/shared-contexts/"+used).with(user("operator").roles("OPERADOR")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/shared-contexts/"+used).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from contexto_compartilhado where id=?",Integer.class,used)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from contexto_compartilhado_compartimento where contexto_id=?",Integer.class,used)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from medicao where id=?",Integer.class,measurement)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from contexto_coleta_execucao where integracao_id=?",Integer.class,integration)).isEqualTo(1);
    }

    private IntegrationRequest request(String credential){return new IntegrationRequest("weather-e2e","WeatherAPI E2E",true,"http://localhost:"+server.getAddress().getPort()+"/current","GET",null,"QUERY",credential,null,"key","q","{}","/current/temp_c","/current/last_updated_epoch","UNIX_SECONDS",null,60,3000,1);}
    private IntegrationRequest genericRequest(String sensor,String auth,String credential,String header,String queryName,java.util.List<HttpMappingRequest> mappings){return new IntegrationRequest(
            sensor,"Generic HTTP","Description",true,"Generic Provider","Provider attribution","http://localhost:"+server.getAddress().getPort()+"/current",
            "POST","application/json","{\"at\":\"{{execution.timestamp}}\",\"where\":\"{{context.coordinates}}\"}",auth,credential,header,queryName,null,
            "{\"coordinates\":\"{{context.coordinates}}\"}","[{\"key\":\"X-Latitude\",\"value\":\"{{context.latitude}}\"}]",mappings,null,
            "/current/last_updated_epoch","UNIX_SECONDS",null,null,1800,5000,2,10,3600,true);}
}
