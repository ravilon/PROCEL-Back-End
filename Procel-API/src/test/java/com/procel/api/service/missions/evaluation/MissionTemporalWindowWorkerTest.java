package com.procel.api.service.missions.evaluation;

import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.service.missions.EventoJanelaAvaliacaoService;
import com.procel.api.service.missions.EventoJanelaAvaliacaoService.EventoJanelaWork;
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

class MissionTemporalWindowWorkerTest {
    EventoJanelaAvaliacaoService janelaService;
    MissionTemporalWindowEvaluationService evaluationService;
    MissionTemporalActivityProcessor temporalActivityProcessor;
    MissionEvaluationProperties properties;
    MissionTemporalWindowWorker worker;

    @BeforeEach
    void setUp() {
        janelaService = mock(EventoJanelaAvaliacaoService.class);
        evaluationService = mock(MissionTemporalWindowEvaluationService.class);
        temporalActivityProcessor = mock(MissionTemporalActivityProcessor.class);
        when(janelaService.countBacklog()).thenReturn(0L);
        properties = new MissionEvaluationProperties();
        properties.getTemporalWindows().setWorkerEnabled(true);
        properties.getTemporalWindows().setBatchSize(1);
        properties.getTemporalWindows().setLeaseDuration(Duration.ofSeconds(30));
        properties.getTemporalWindows().setMaxAttempts(2);
        properties.getTemporalWindows().setInitialBackoff(Duration.ofSeconds(1));
        properties.getTemporalWindows().setMaxBackoff(Duration.ofSeconds(10));
        worker = new MissionTemporalWindowWorker(
                janelaService,
                evaluationService,
                temporalActivityProcessor,
                properties,
                new ApiObservabilityMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void workerDesabilitadoNaoReivindicaJanelas() {
        properties.getTemporalWindows().setWorkerEnabled(false);

        assertThat(worker.processAvailableBatch()).isZero();

        verify(janelaService, never()).claimAvailable(anyInt(), any(Duration.class), anyInt());
    }

    @Test
    void janelaClaimadaEhAvaliada() {
        EventoJanelaWork work = work(1);
        when(janelaService.claimAvailable(1, Duration.ofSeconds(30), 2)).thenReturn(List.of(work));
        when(evaluationService.evaluateClaimed(eq(work.janelaId()), any(Instant.class)))
                .thenReturn(MissionTemporalWindowEvaluationService.WindowEvaluationOutcome.satisfied());

        assertThat(worker.processAvailableBatch()).isEqualTo(1);

        verify(janelaService).expirarJanelasVencidas(any(Instant.class));
        verify(evaluationService).evaluateClaimed(eq(work.janelaId()), any(Instant.class));
    }

    @Test
    void avaliacaoAdiadaVoltaParaRetry() {
        EventoJanelaWork work = work(1);
        when(janelaService.claimAvailable(1, Duration.ofSeconds(30), 2)).thenReturn(List.of(work));
        when(evaluationService.evaluateClaimed(eq(work.janelaId()), any(Instant.class)))
                .thenReturn(MissionTemporalWindowEvaluationService.WindowEvaluationOutcome.skipped("not ready"));

        assertThat(worker.processAvailableBatch()).isEqualTo(1);

        verify(janelaService).marcarRetry(eq(work.janelaId()), any(Instant.class), eq("not ready"));
    }

    @Test
    void maxAttemptsMarcaFailed() {
        EventoJanelaWork work = work(2);
        when(janelaService.claimAvailable(1, Duration.ofSeconds(30), 2)).thenReturn(List.of(work));

        assertThat(worker.processAvailableBatch()).isEqualTo(1);

        verify(janelaService).marcarFailed(work.janelaId(), "Temporal window reached max attempts");
    }

    private EventoJanelaWork work(int attempts) {
        return new EventoJanelaWork(UUID.randomUUID(), UUID.randomUUID(), "ROOM", attempts);
    }
}
