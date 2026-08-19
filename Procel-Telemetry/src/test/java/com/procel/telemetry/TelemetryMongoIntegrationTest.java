package com.procel.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.entity.TelemetrySource;
import com.procel.telemetry.exception.ApiStatusException;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import com.procel.telemetry.service.TelemetryIngestService;
import com.procel.telemetry.service.canonical.CanonicalTelemetryWorker;
import com.procel.telemetry.service.canonical.RawTelemetryClaimService;
import com.procel.telemetry.service.mqtt.MqttTelemetrySubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
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
        registry.add("procel.telemetry.canonical-worker.enabled", () -> "false");
        registry.add("procel.telemetry.canonical-worker.batch-size", () -> "3");
        registry.add("procel.telemetry.canonical-worker.max-attempts", () -> "2");
        registry.add("procel.telemetry.canonical-worker.backoff", () -> "PT1S,PT2S");
        registry.add("procel.telemetry.canonical-worker.lease-timeout", () -> "PT30S");
    }

    @Autowired RawTelemetryEventRepository repository;
    @Autowired TelemetryIngestService ingestService;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired RawTelemetryClaimService claimService;
    @Autowired ApplicationContext applicationContext;

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
                "idx_raw_telemetry_claim",
                "idx_raw_telemetry_processing_lock",
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
    void canonicalWorkerIsDisabledByDefault() {
        assertThat(applicationContext.getBeansOfType(CanonicalTelemetryWorker.class)).isEmpty();
    }

    @Test
    void mqttSubscriberIsDisabledByDefault() {
        assertThat(applicationContext.getBeansOfType(MqttTelemetrySubscriber.class)).isEmpty();
    }

    @Test
    void twoWorkersDoNotProcessTheSameReceivedEvent() throws Exception {
        insertRaw("claim-race", "msg-claim", RawTelemetryStatus.RECEIVED, 0, null);
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<String> task = () -> {
                RawTelemetryEvent claimed = claimService.claimNext("worker-" + Thread.currentThread().threadId(), Instant.now());
                return claimed != null ? claimed.getId() : null;
            };
            var futures = executor.invokeAll(java.util.Collections.nCopies(8, task));

            long claimedCount = futures.stream().filter(future -> {
                try {
                    return future.get(10, TimeUnit.SECONDS) != null;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).count();

            assertThat(claimedCount).isEqualTo(1);
            RawTelemetryEvent event = mongoTemplate.findById("claim-race", RawTelemetryEvent.class);
            assertThat(event.getStatus()).isEqualTo(RawTelemetryStatus.PROCESSING);
            assertThat(event.getProcessing().getAttempts()).isEqualTo(1);
            assertThat(event.getProcessing().getWorkerId()).startsWith("worker-");
            assertThat(event.getProcessing().getLockedAt()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retryBackoffAndMaxAttemptsArePersisted() {
        insertRaw("retry-1", "msg-retry-1", RawTelemetryStatus.RECEIVED, 0, null);
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        RawTelemetryEvent first = claimService.claimNext("worker-retry", now);
        claimService.retryOrFail(first, "HTTP_500", now);

        RawTelemetryEvent retried = mongoTemplate.findById("retry-1", RawTelemetryEvent.class);
        assertThat(retried.getStatus()).isEqualTo(RawTelemetryStatus.RECEIVED);
        assertThat(retried.getProcessing().getNextAttemptAt()).isEqualTo(now.plusSeconds(1));
        assertThat(retried.getProcessing().getLastError()).isEqualTo("HTTP_500");

        insertRaw("retry-2", "msg-retry-2", RawTelemetryStatus.RECEIVED, 1, null);
        RawTelemetryEvent finalAttempt = claimService.claimNext("worker-retry", now);
        claimService.retryOrFail(finalAttempt, "HTTP_500", now);

        RawTelemetryEvent failed = mongoTemplate.findById("retry-2", RawTelemetryEvent.class);
        assertThat(failed.getStatus()).isEqualTo(RawTelemetryStatus.CANONICAL_FAILED);
        assertThat(failed.getProcessing().getLastError()).isEqualTo("HTTP_500");
    }

    @Test
    void recoversStuckProcessingEventsAndFailsExhaustedOnes() {
        Instant old = Instant.parse("2026-08-19T12:00:00Z");
        Instant now = old.plusSeconds(60);
        insertRaw("stuck-retry", "msg-stuck-retry", RawTelemetryStatus.PROCESSING, 1, old);
        insertRaw("stuck-fail", "msg-stuck-fail", RawTelemetryStatus.PROCESSING, 2, old);

        long recovered = claimService.recoverStuck("worker-recover", now);

        assertThat(recovered).isEqualTo(2);
        RawTelemetryEvent retry = mongoTemplate.findById("stuck-retry", RawTelemetryEvent.class);
        RawTelemetryEvent fail = mongoTemplate.findById("stuck-fail", RawTelemetryEvent.class);
        assertThat(retry.getStatus()).isEqualTo(RawTelemetryStatus.RECEIVED);
        assertThat(retry.getProcessing().getNextAttemptAt()).isEqualTo(now);
        assertThat(fail.getStatus()).isEqualTo(RawTelemetryStatus.CANONICAL_FAILED);
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

    private void insertRaw(
            String id,
            String messageId,
            RawTelemetryStatus status,
            int attempts,
            Instant lockedAt
    ) {
        Document processing = new Document("attempts", attempts);
        if (lockedAt != null) {
            processing.append("lockedAt", Date.from(lockedAt)).append("workerId", "old-worker");
        }
        mongoTemplate.getCollection("raw_telemetry_events").insertOne(new Document()
                .append("_id", id)
                .append("producerId", "producer")
                .append("source", "REST")
                .append("messageId", messageId)
                .append("sensorId", "sensor-1")
                .append("sourceTimestamp", Date.from(Instant.parse("2026-08-19T11:59:59Z")))
                .append("receivedAt", Date.from(Instant.parse("2026-08-19T12:00:00Z")))
                .append("payload", new Document("value", 1))
                .append("payloadHash", messageId)
                .append("status", status.name())
                .append("processing", processing)
                .append("expiresAt", Date.from(Instant.parse("2026-08-20T12:00:00Z"))));
    }
}
