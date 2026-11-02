package com.procel.api.service.shared;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.slf4j.LoggerFactory.getLogger;

@SpringBootTest(properties={"procel.sensors.seed-path=missing-test-sensors.json","procel.security.bootstrap-admin.enabled=false",
        "procel.shared-context.collector.enabled=true","procel.shared-context.collector.poll-interval=1h","procel.shared-context.collector.allow-loopback=true"})
@Testcontainers(disabledWithoutDocker=true)
class SharedContextCollectorFailureAndLeaseTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16");
    static HttpServer server; static final AtomicReference<String> mode=new AtomicReference<>("OK");
    static final AtomicInteger requests=new AtomicInteger(); static volatile CountDownLatch entered,release;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("PROCEL_CREDENTIAL_ENCRYPTION_KEY",()->"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");}
    @BeforeAll static void server() throws Exception {server=HttpServer.create(new InetSocketAddress(0),0);server.setExecutor(Executors.newCachedThreadPool());server.createContext("/weather",exchange->{requests.incrementAndGet();String current=mode.get();if("BLOCK_ONCE".equals(current)&&mode.compareAndSet("BLOCK_ONCE","OK")){entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}if("TIMEOUT".equals(current))try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}int status="HTTP_ERROR".equals(current)?503:200;String body=switch(current){case "INVALID"->"not-json";case "MISSING"->"{\"current\":{}}";default->"{\"current\":{\"temp_c\":20.5,\"last_updated_epoch\":1793577600}}";};byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();}
    @AfterAll static void stop(){if(server!=null)server.stop(0);}
    @Autowired JdbcTemplate jdbc; @Autowired SharedContextCollector collector; @Autowired SharedContextService contexts; @Autowired CredentialEncryptionService encryption;
    UUID context,integration; String sensor;

    @BeforeEach void setup(){mode.set("OK");requests.set(0);context=UUID.randomUUID();integration=UUID.randomUUID();sensor="weather-"+integration;jdbc.update("insert into tipo_de_sensor(nome) values (?)",sensor);UUID parameter=UUID.randomUUID();jdbc.update("insert into parametro_def(id,data_type,numeric_unit,tipo_nome,nome,descricao,ativo) values (?,'NUMERIC','C',?,'temperatura_externa','External',true)",parameter,sensor);jdbc.update("insert into contexto_compartilhado(id,nome,location_mode,latitude,longitude) values (?,?,'OWN',-31.7,-52.3)",context,sensor);jdbc.update("insert into sensor(external_id,nome,tipo_nome,contexto_compartilhado_id,ativo) values (?,?,?,?,true)",sensor,sensor,sensor,context);jdbc.update("""
        insert into contexto_integracao_externa(id,contexto_id,sensor_external_id,nome,endpoint_url,metodo,auth_mode,
          credential_query_param,temperature_json_pointer,observed_at_json_pointer,observed_at_format,interval_seconds,timeout_millis,max_attempts,next_run_at)
        values (?,?,?,?,?,'GET','QUERY','key','/current/temp_c','/current/last_updated_epoch','UNIX_SECONDS',60,150,1,now()+interval '1 day')
        """,integration,context,sensor,sensor,"http://127.0.0.1:"+server.getAddress().getPort()+"/weather");var encrypted=encryption.encrypt(integration,"never-print-this-secret");jdbc.update("update contexto_integracao_externa set credential_ciphertext=?,credential_iv=? where id=?",encrypted.ciphertext(),encrypted.iv(),integration);}

    @Test void reportsTimeoutHttpInvalidAndMissingFieldsWithoutLeakingSecret(){var logger=(ch.qos.logback.classic.Logger)getLogger(SharedContextCollector.class);var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);try{for(String failure:new String[]{"TIMEOUT","HTTP_ERROR","INVALID","MISSING"}){mode.set(failure);assertThatThrownBy(()->collector.collectNow(integration)).isInstanceOf(IllegalStateException.class).satisfies(error->assertThat(error.toString()).doesNotContain("never-print-this-secret"));String error=jdbc.queryForObject("select last_error from contexto_integracao_externa where id=?",String.class,integration);assertThat(error).isNotBlank().doesNotContain("never-print-this-secret");}var response=contexts.integrations(context).getFirst();assertThat(response.toString()).doesNotContain("never-print-this-secret");assertThat(response.credentialConfigured()).isTrue();assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).allSatisfy(message->assertThat(message).doesNotContain("never-print-this-secret"));}finally{logger.detachAppender(logs);}}

    @Test void scheduledLeaseBlocksManualCollection(){entered=new CountDownLatch(1);release=new CountDownLatch(1);mode.set("BLOCK_ONCE");jdbc.update("update contexto_integracao_externa set next_run_at=now(),timeout_millis=3000 where id=?",integration);ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> scheduled=pool.submit(collector::scheduled);try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->collector.collectNow(integration)).hasMessageContaining("already running");release.countDown();scheduled.get(5,TimeUnit.SECONDS);assertThat(requests.get()).isEqualTo(1);}catch(Exception e){throw new AssertionError(e);}finally{release.countDown();pool.shutdownNow();}}

    @Test void expiredLeaseCanBeRecoveredAndOldOwnerCannotFinalize(){entered=new CountDownLatch(1);release=new CountDownLatch(1);mode.set("BLOCK_ONCE");jdbc.update("update contexto_integracao_externa set timeout_millis=3000 where id=?",integration);ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> old=pool.submit(()->collector.collectNow(integration));try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();String owner=jdbc.queryForObject("select locked_by from contexto_integracao_externa where id=?",String.class,integration);jdbc.update("update contexto_integracao_externa set locked_until=now()-interval '1 second' where id=?",integration);assertThat(collector.collectNow(integration).outcome()).isEqualTo("NEW_MEASUREMENT");String newOutcome=jdbc.queryForObject("select last_outcome from contexto_integracao_externa where id=?",String.class,integration);release.countDown();assertThatThrownBy(()->old.get(5,TimeUnit.SECONDS)).hasRootCauseMessage("collection lease lost");assertThat(jdbc.queryForObject("select last_outcome from contexto_integracao_externa where id=?",String.class,integration)).isEqualTo(newOutcome);assertThat(jdbc.queryForObject("select count(*) from contexto_coleta_execucao where integracao_id=?",Integer.class,integration)).isEqualTo(1);assertThat(owner).isNotBlank();}catch(Exception e){throw new AssertionError(e);}finally{release.countDown();pool.shutdownNow();}}
}
