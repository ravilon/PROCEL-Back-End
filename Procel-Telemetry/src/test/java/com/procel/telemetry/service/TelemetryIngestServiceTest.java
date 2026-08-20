package com.procel.telemetry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.observability.TelemetryObservabilityMetrics;
import com.procel.telemetry.repository.RawTelemetryEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryIngestServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void doesNotConvertNonIdempotencyDuplicateKeyException() throws Exception {
        RawTelemetryEventRepository repository = mock(RawTelemetryEventRepository.class);
        when(repository.save(any())).thenThrow(new DuplicateKeyException("duplicate key on other_index"));

        TelemetryIngestService service = new TelemetryIngestService(
                repository,
                new PayloadHashService(objectMapper),
                new TelemetryProperties(),
                objectMapper,
                new TelemetryObservabilityMetrics(new SimpleMeterRegistry(), repository)
        );

        assertThatThrownBy(() -> service.ingest("producer", objectMapper.readTree("""
                {"source":"REST","messageId":"msg","payload":{"value":1}}
                """)))
                .isInstanceOf(DuplicateKeyException.class);

        verify(repository, never()).findByProducerIdAndSourceAndMessageId(any(), any(), any());
    }
}
