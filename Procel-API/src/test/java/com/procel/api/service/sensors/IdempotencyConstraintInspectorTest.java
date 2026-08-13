package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.entity.sensors.MedicaoIngestaoSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IdempotencyConstraintInspectorTest {
    private final IdempotencyConstraintInspector inspector = new IdempotencyConstraintInspector();

    @Test
    void recognizesOnlyKnownIdempotencyConstraints() {
        assertThat(inspector.idempotencyConstraint(violation("ux_metadata_direct_idempotency")))
                .isEqualTo(IdempotencyConstraintInspector.IdempotencyConstraint.DIRECT);
        assertThat(inspector.idempotencyConstraint(violation("ux_metadata_profile_idempotency")))
                .isEqualTo(IdempotencyConstraintInspector.IdempotencyConstraint.PROFILE);

        assertThat(inspector.idempotencyConstraint(violation("ux_sensor_integration_profile_nome")))
                .isEqualTo(IdempotencyConstraintInspector.IdempotencyConstraint.NONE);
        assertThat(inspector.idempotencyConstraint(violation("fk_metadata_integration_profile")))
                .isEqualTo(IdempotencyConstraintInspector.IdempotencyConstraint.NONE);
        assertThat(inspector.idempotencyConstraint(violation("ck_medicao_ingestao_integration_context")))
                .isEqualTo(IdempotencyConstraintInspector.IdempotencyConstraint.NONE);
        assertThat(inspector.idempotencyConstraint(violation(null)))
                .isEqualTo(IdempotencyConstraintInspector.IdempotencyConstraint.NONE);
    }

    @Test
    void unknownConstraintViolationsArePropagatedByOrchestrator() {
        var transaction = mock(SensorCanonicalIngestionTransaction.class);
        var duplicateReader = mock(SensorIngestDuplicateReader.class);
        var fingerprintService = mock(PayloadFingerprintService.class);
        var orchestrator = new SensorIngestOrchestrator(
                transaction,
                duplicateReader,
                inspector,
                fingerprintService
        );
        var request = request("msg-unknown");
        var violation = new DataIntegrityViolationException("unknown unique", violation("ux_other_unique"));
        when(transaction.ingestNew("producer", request)).thenThrow(violation);

        assertThatThrownBy(() -> orchestrator.ingest("producer", request))
                .isSameAs(violation);
        verifyNoInteractions(duplicateReader);
        verifyNoInteractions(fingerprintService);
    }

    private SensorIngestDTOs.CanonicalIngestRequest request(String messageId) {
        return new SensorIngestDTOs.CanonicalIngestRequest(
                messageId,
                "SII-001",
                Instant.parse("2026-08-11T23:30:00Z"),
                MedicaoIngestaoSource.API,
                null,
                Map.of("temperature_c", 23.7)
        );
    }

    private ConstraintViolation violation(String constraint) {
        return new ConstraintViolation(constraint);
    }

    public static class ConstraintViolation extends RuntimeException {
        private final ServerError serverError;

        ConstraintViolation(String constraint) {
            this.serverError = constraint == null ? null : new ServerError(constraint);
        }

        public ServerError getServerErrorMessage() {
            return serverError;
        }
    }

    public static class ServerError {
        private final String constraint;

        ServerError(String constraint) {
            this.constraint = constraint;
        }

        public String getConstraint() {
            return constraint;
        }
    }
}
