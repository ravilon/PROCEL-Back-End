package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.dto.sensors.SensorTelemetryIngestDTOs;
import com.procel.api.entity.rooms.*;
import com.procel.api.entity.sensors.*;
import com.procel.api.repository.rooms.*;
import com.procel.api.repository.sensors.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(SensorIngestTransactionBoundaryTest.DuplicateReaderConfig.class)
class SensorIngestTransactionBoundaryTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("procel.security.bootstrap-admin.enabled", () -> "false");
        registry.add("procel.sensors.seed-path", () -> "missing-test-sensors.json");
    }

    @Autowired SensorIngestOrchestrator orchestrator;
    @Autowired DuplicateReaderRecorder duplicateReaderRecorder;
    @Autowired CampusRepository campusRepo;
    @Autowired UnidadeRepository unidadeRepo;
    @Autowired PredioRepository predioRepo;
    @Autowired CompartimentoRepository compartimentoRepo;
    @Autowired TipoDeSensorRepository tipoRepo;
    @Autowired SensorRepository sensorRepo;
    @Autowired ParametroDefRepository parametroRepo;
    @Autowired MedicaoRepository medicaoRepo;
    @Autowired ParametroValorRepository parametroValorRepo;
    @Autowired MedicaoIngestaoMetadataRepository metadataRepo;
    @Autowired SensorIntegrationProfileRepository profileRepo;
    @Autowired SensorIntegrationParserVersionRepository versionRepo;
    @Autowired SensorIntegrationBindingRepository bindingRepo;
    @MockitoBean ParametroQualificacaoService qualificacaoService;

    private String sensorId;

    @BeforeEach
    void setUp() {
        duplicateReaderRecorder.reset();
        sensorId = "SII-TX-" + UUID.randomUUID();
        Campus campus = campusRepo.save(new Campus("Campus " + sensorId));
        Unidade unidade = unidadeRepo.save(new Unidade("Unidade " + sensorId));
        Predio predio = predioRepo.save(new Predio(campus, "Predio " + sensorId));
        Compartimento compartimento = compartimentoRepo.save(
                new Compartimento("ROOM-" + sensorId, predio, unidade, "Sala " + sensorId, "Sala"));
        TipoDeSensor tipo = tipoRepo.save(new TipoDeSensor("TYPE-" + sensorId));
        parametroRepo.save(new ParametroDef(tipo, "temperature_c", "Temperatura", DataType.NUMERIC, "C"));
        sensorRepo.save(new Sensor(sensorId, "Sensor " + sensorId, tipo, compartimento));
    }

    @Test
    void duplicateReaderRunsAfterLosingTransactionRollbackInRequiresNewReadOnlyTransaction() {
        String producer = "producer-boundary";
        String messageId = "msg-boundary-" + UUID.randomUUID();
        var original = request(messageId, new BigDecimal("23.70"));
        var divergent = request(messageId, new BigDecimal("24.10"));

        var created = orchestrator.ingest(producer, original);
        assertThat(created.status().value()).isEqualTo(201);
        assertThat(created.response().code()).isEqualTo("MEASUREMENT_INGESTED");

        long metadataAfterWinner = metadataRepo.count();
        long medicoesAfterWinner = medicaoRepo.count();
        long valoresAfterWinner = parametroValorRepo.count();

        duplicateReaderRecorder.reset();
        var equivalentDuplicate = orchestrator.ingest(producer, original);
        assertThat(equivalentDuplicate.status().value()).isEqualTo(200);
        assertThat(equivalentDuplicate.response().code()).isEqualTo("DUPLICATE_MESSAGE");
        assertThat(equivalentDuplicate.response().medicaoId()).isEqualTo(created.response().medicaoId());
        assertDuplicateReaderBoundary(metadataAfterWinner, medicoesAfterWinner, valoresAfterWinner);

        duplicateReaderRecorder.reset();
        var divergentDuplicate = orchestrator.ingest(producer, divergent);
        assertThat(divergentDuplicate.status().value()).isEqualTo(409);
        assertThat(divergentDuplicate.response().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(divergentDuplicate.response().medicaoId()).isEqualTo(created.response().medicaoId());
        assertDuplicateReaderBoundary(metadataAfterWinner, medicoesAfterWinner, valoresAfterWinner);
    }

    @Test
    void telemetryDuplicateReaderRunsAfterLosingTransactionRollbackInRequiresNewReadOnlyTransaction() {
        var context = activeProfile();
        String rawMessageId = "raw-boundary-" + UUID.randomUUID();
        var rawContext = rawContext(rawMessageId);
        var original = request(rawMessageId, new BigDecimal("23.70"));
        var divergent = request(rawMessageId, new BigDecimal("24.10"));

        var created = orchestrator.ingestTelemetryRawWithProfile(
                context.profile().getId(),
                context.version().getId(),
                "telemetry-service",
                rawContext,
                original
        );
        assertThat(created.status().value()).isEqualTo(201);
        assertThat(created.response().code()).isEqualTo("MEASUREMENT_INGESTED");

        long metadataAfterWinner = metadataRepo.count();
        long medicoesAfterWinner = medicaoRepo.count();
        long valoresAfterWinner = parametroValorRepo.count();

        duplicateReaderRecorder.reset();
        var equivalentDuplicate = orchestrator.ingestTelemetryRawWithProfile(
                context.profile().getId(),
                context.version().getId(),
                "telemetry-service",
                rawContext,
                original
        );
        assertThat(equivalentDuplicate.status().value()).isEqualTo(200);
        assertThat(equivalentDuplicate.response().code()).isEqualTo("DUPLICATE_MESSAGE");
        assertDuplicateReaderBoundary(metadataAfterWinner, medicoesAfterWinner, valoresAfterWinner);

        duplicateReaderRecorder.reset();
        var divergentDuplicate = orchestrator.ingestTelemetryRawWithProfile(
                context.profile().getId(),
                context.version().getId(),
                "telemetry-service",
                rawContext,
                divergent
        );
        assertThat(divergentDuplicate.status().value()).isEqualTo(409);
        assertThat(divergentDuplicate.response().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertDuplicateReaderBoundary(metadataAfterWinner, medicoesAfterWinner, valoresAfterWinner);
    }

    private void assertDuplicateReaderBoundary(long metadataAfterWinner, long medicoesAfterWinner, long valoresAfterWinner) {
        assertThat(duplicateReaderRecorder.calls).isEqualTo(1);
        assertThat(duplicateReaderRecorder.activeTransactionAtRead).isTrue();
        assertThat(duplicateReaderRecorder.readOnlyAtRead).isTrue();
        assertThat(duplicateReaderRecorder.rollbackOnlyAtRead).isFalse();
        assertThat(duplicateReaderRecorder.metadataCountAtRead).isEqualTo(metadataAfterWinner);
        assertThat(duplicateReaderRecorder.medicaoCountAtRead).isEqualTo(medicoesAfterWinner);
        assertThat(duplicateReaderRecorder.parametroValorCountAtRead).isEqualTo(valoresAfterWinner);
        assertThat(metadataRepo.count()).isEqualTo(metadataAfterWinner);
        assertThat(medicaoRepo.count()).isEqualTo(medicoesAfterWinner);
        assertThat(parametroValorRepo.count()).isEqualTo(valoresAfterWinner);
    }

    private SensorIngestDTOs.CanonicalIngestRequest request(String messageId, BigDecimal temperature) {
        return new SensorIngestDTOs.CanonicalIngestRequest(
                messageId,
                sensorId,
                Instant.parse("2026-08-11T23:30:00Z"),
                MedicaoIngestaoSource.MQTT,
                Instant.parse("2026-08-11T23:30:02Z"),
                Map.of("temperature_c", temperature)
        );
    }

    private SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest rawContext(String rawMessageId) {
        return new SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest(
                "raw-event-" + UUID.randomUUID(),
                "original-producer",
                rawMessageId,
                Instant.parse("2026-08-12T10:00:00Z"),
                Instant.parse("2026-08-12T09:59:58Z"),
                null
        );
    }

    private ActiveContext activeProfile() {
        var profile = profileRepo.save(new SensorIntegrationProfile("Profile " + UUID.randomUUID(), null, MedicaoIngestaoSource.MQTT));
        var version = new SensorIntegrationParserVersion(
                profile,
                versionRepo.maxVersionByProfile(profile.getId()) + 1,
                SensorResolutionMode.PAYLOAD_POINTER,
                "/meta/id",
                "/device/id",
                "/measuredAt",
                "/receivedAt"
        );
        version.replaceMappings(java.util.List.of(
                new SensorIntegrationValueMapping("temperature_c", "/readings/temperature", true)
        ));
        version.activate(Instant.now());
        version = versionRepo.save(version);
        bindingRepo.save(new SensorIntegrationBinding(sensorRepo.findById(sensorId).orElseThrow(), profile));
        return new ActiveContext(profile, version);
    }

    @TestConfiguration
    static class DuplicateReaderConfig {
        @Bean
        @Primary
        RecordingDuplicateReader recordingDuplicateReader(
                MedicaoIngestaoMetadataRepository metadataRepo,
                MedicaoRepository medicaoRepo,
                ParametroValorRepository parametroValorRepo,
                DuplicateReaderRecorder recorder
        ) {
            return new RecordingDuplicateReader(metadataRepo, medicaoRepo, parametroValorRepo, recorder);
        }

        @Bean
        DuplicateReaderRecorder duplicateReaderRecorder() {
            return new DuplicateReaderRecorder();
        }

        @Bean
        @Primary
        SensorIngestOrchestrator recordingOrchestrator(
                SensorCanonicalIngestionTransaction ingestionTransaction,
                RecordingDuplicateReader duplicateReader,
                IdempotencyConstraintInspector constraintInspector,
                PayloadFingerprintService fingerprintService
        ) {
            return new SensorIngestOrchestrator(
                    ingestionTransaction,
                    duplicateReader,
                    constraintInspector,
                    fingerprintService
            );
        }
    }

    static class RecordingDuplicateReader extends SensorIngestDuplicateReader {
        private final MedicaoIngestaoMetadataRepository metadataRepo;
        private final MedicaoRepository medicaoRepo;
        private final ParametroValorRepository parametroValorRepo;
        private final DuplicateReaderRecorder recorder;

        RecordingDuplicateReader(
                MedicaoIngestaoMetadataRepository metadataRepo,
                MedicaoRepository medicaoRepo,
                ParametroValorRepository parametroValorRepo,
                DuplicateReaderRecorder recorder
        ) {
            super(metadataRepo);
            this.metadataRepo = metadataRepo;
            this.medicaoRepo = medicaoRepo;
            this.parametroValorRepo = parametroValorRepo;
            this.recorder = recorder;
        }

        @Override
        @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
        public DuplicateLookupResult findByProducerSensorMessage(
                String producerId,
                String sensorExternalId,
                String messageId
        ) {
            recordTransactionState();
            return super.findByProducerSensorMessage(producerId, sensorExternalId, messageId);
        }

        @Override
        @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
        public DuplicateLookupResult findByTelemetryRawKey(
                UUID integrationProfileId,
                String sensorExternalId,
                String originalProducerId,
                String rawMessageId
        ) {
            recordTransactionState();
            return super.findByTelemetryRawKey(integrationProfileId, sensorExternalId, originalProducerId, rawMessageId);
        }

        private void recordTransactionState() {
            recorder.calls++;
            recorder.activeTransactionAtRead = TransactionSynchronizationManager.isActualTransactionActive();
            recorder.readOnlyAtRead = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            TransactionStatus status = TransactionAspectSupport.currentTransactionStatus();
            recorder.rollbackOnlyAtRead = status.isRollbackOnly();
            recorder.metadataCountAtRead = metadataRepo.count();
            recorder.medicaoCountAtRead = medicaoRepo.count();
            recorder.parametroValorCountAtRead = parametroValorRepo.count();
        }
    }

    static class DuplicateReaderRecorder {
        private int calls;
        private boolean activeTransactionAtRead;
        private boolean readOnlyAtRead;
        private boolean rollbackOnlyAtRead;
        private long metadataCountAtRead;
        private long medicaoCountAtRead;
        private long parametroValorCountAtRead;

        void reset() {
            calls = 0;
            activeTransactionAtRead = false;
            readOnlyAtRead = false;
            rollbackOnlyAtRead = true;
            metadataCountAtRead = -1;
            medicaoCountAtRead = -1;
            parametroValorCountAtRead = -1;
        }
    }

    private record ActiveContext(SensorIntegrationProfile profile, SensorIntegrationParserVersion version) {}
}
