package com.procel.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.entity.TelemetrySource;
import com.procel.telemetry.exception.ApiStatusException;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import com.procel.telemetry.service.TelemetryIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TelemetryMongoIntegrationTest {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("procel.security.jwt.secret", () -> TestJwt.SECRET);
    }

    @Autowired RawTelemetryEventRepository repository;
    @Autowired TelemetryIngestService ingestService;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void createsUniqueAndTtlIndexes() {
        List<String> names = mongoTemplate.indexOps(RawTelemetryEvent.class)
                .getIndexInfo()
                .stream()
                .map(index -> index.getName())
                .toList();

        assertThat(names).contains(
                "ux_raw_telemetry_idempotency",
                "idx_raw_telemetry_sensor_received",
                "idx_raw_telemetry_status_received",
                "idx_raw_telemetry_received",
                "ttl_raw_telemetry_expires_at"
        );

        var ttl = mongoTemplate.indexOps(RawTelemetryEvent.class)
                .getIndexInfo()
                .stream()
            .filter(index -> index.getName().equals("ttl_raw_telemetry_expires_at"))
                .findFirst()
                .orElseThrow();
        assertThat(ttl.getExpireAfter()).isNotNull();

        var unique = mongoTemplate.indexOps(RawTelemetryEvent.class)
                .getIndexInfo()
                .stream()
                .filter(index -> index.getName().equals("ux_raw_telemetry_idempotency"))
                .findFirst()
                .orElseThrow();
        assertThat(unique.isUnique()).isTrue();
    }

    @Test
    void concurrentIngestUsesUniqueIndexAndReturnsSingleWinner() throws Exception {
        var request = objectMapper.readTree("""
                {"source":"REST","messageId":"race-1","sensorId":"sensor-1","payload":{"value":1}}
                """);
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<TelemetryEventDTOs.IngestResponse> task = () -> ingestService.ingest("producer-race", request);
            var futures = executor.invokeAll(java.util.Collections.nCopies(8, task));

            int duplicates = 0;
            for (var future : futures) {
                if (future.get(10, TimeUnit.SECONDS).duplicate()) duplicates++;
            }

            assertThat(duplicates).isEqualTo(7);
            assertThat(repository.count()).isEqualTo(1);
            RawTelemetryEvent event = repository.findAll().getFirst();
            assertThat(event.getStatus()).isEqualTo(RawTelemetryStatus.RECEIVED);
            assertThat(event.getSource()).isEqualTo(TelemetrySource.REST);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDivergentIngestCreatesOneWinnerAndConflictsTheRest() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<String> first = () -> ingestResult("""
                    {"source":"REST","messageId":"race-divergent","sensorId":"sensor-1","payload":{"value":1}}
                    """);
            Callable<String> second = () -> ingestResult("""
                    {"source":"REST","messageId":"race-divergent","sensorId":"sensor-1","payload":{"value":2}}
                    """);
            var futures = executor.invokeAll(List.of(first, second, first, second, first, second, first, second));

            int createdOrDuplicate = 0;
            int conflicts = 0;
            for (var future : futures) {
                String result = future.get(10, TimeUnit.SECONDS);
                if ("accepted".equals(result)) createdOrDuplicate++;
                if ("RAW_IDEMPOTENCY_CONFLICT".equals(result)) conflicts++;
            }

            assertThat(repository.count()).isEqualTo(1);
            assertThat(createdOrDuplicate).isGreaterThanOrEqualTo(1);
            assertThat(conflicts).isGreaterThanOrEqualTo(1);
            assertThat(createdOrDuplicate + conflicts).isEqualTo(8);
        } finally {
            executor.shutdownNow();
        }
    }

    private String ingestResult(String json) throws Exception {
        try {
            ingestService.ingest("producer-race", objectMapper.readTree(json));
            return "accepted";
        } catch (ApiStatusException ex) {
            return ex.getError();
        }
    }
}
