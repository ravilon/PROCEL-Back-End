package com.procel.api.service.missions.evaluation;

import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.service.missions.EventoAvaliacaoRequestService;
import com.procel.api.service.missions.EventoAvaliacaoRequestService.EventoAvaliacaoWork;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionEventEvaluationWorkerTest {
    EventoAvaliacaoRequestService requestService;
    MissionEventEvaluationProcessor processor;
    MissionEvaluationProperties properties;
    SimpleMeterRegistry registry;
    MissionEventEvaluationWorker worker;

    @BeforeEach
    void setUp() {
        requestService = mock(EventoAvaliacaoRequestService.class);
        processor = mock(MissionEventEvaluationProcessor.class);
        properties = new MissionEvaluationProperties();
        properties.setWorkerEnabled(true);
        properties.setBatchSize(1);
        properties.setLeaseDuration(Duration.ofSeconds(30));
        properties.setMaxAttempts(2);
        properties.setInitialBackoff(Duration.ofSeconds(1));
        properties.setMaxBackoff(Duration.ofSeconds(10));
        registry = new SimpleMeterRegistry();
        when(requestService.countBacklog()).thenReturn(0L);
        worker = new MissionEventEvaluationWorker(
                requestService,
                processor,
                properties,
                new ApiObservabilityMetrics(registry)
        );
    }

    @Test
    void disabledWorkerDoesNotClaimRequests() {
        properties.setWorkerEnabled(false);

        assertThat(worker.processAvailableBatch()).isZero();

        verify(requestService, never()).claimAvailable(anyInt(), any(Duration.class), anyInt());
    }

    @Test
    void transientFailureSchedulesRetryAndRecordsMetric() {
        EventoAvaliacaoWork work = work(1);
        when(requestService.claimAvailable(1, Duration.ofSeconds(30), 2)).thenReturn(List.of(work));
        when(processor.process(eq(work), any(Instant.class)))
                .thenThrow(new MissionEventEvaluationFailure("temporary", false));

        assertThat(worker.processAvailableBatch()).isEqualTo(1);

        verify(requestService).markRetry(eq(work.requestId()), any(Instant.class), eq("temporary"));
        assertThat(counter("procel.missions.evaluations.retried")).isEqualTo(1.0);
    }

    @Test
    void maxAttemptsMarksFailedAndRecordsMetric() {
        EventoAvaliacaoWork work = work(2);
        when(requestService.claimAvailable(1, Duration.ofSeconds(30), 2)).thenReturn(List.of(work));
        when(processor.process(eq(work), any(Instant.class)))
                .thenThrow(new MissionEventEvaluationFailure("still temporary", false));

        assertThat(worker.processAvailableBatch()).isEqualTo(1);

        verify(requestService).markFailed(work.requestId(), "still temporary");
        assertThat(counter("procel.missions.evaluations.failed")).isEqualTo(1.0);
    }

    @Test
    void batchSizeIsPassedToClaim() {
        properties.setBatchSize(3);
        when(requestService.claimAvailable(3, Duration.ofSeconds(30), 2)).thenReturn(List.of());

        assertThat(worker.processAvailableBatch()).isZero();

        verify(requestService).claimAvailable(3, Duration.ofSeconds(30), 2);
    }

    private EventoAvaliacaoWork work(int attempts) {
        return new EventoAvaliacaoWork(UUID.randomUUID(), UUID.randomUUID(), attempts);
    }

    private double counter(String name) {
        return registry.find(name).counter().count();
    }
}
