package com.procel.api.service.missions.evaluation;

import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.service.missions.EventoJanelaAvaliacaoService;
import com.procel.api.service.missions.EventoJanelaAvaliacaoService.EventoJanelaWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class MissionTemporalWindowWorker {
    private static final Logger log = LoggerFactory.getLogger(MissionTemporalWindowWorker.class);
    private static final String APPLICATION = "procel-api";

    private final EventoJanelaAvaliacaoService janelaService;
    private final MissionTemporalWindowEvaluationService evaluationService;
    private final MissionEvaluationProperties properties;
    private final String workerId;

    public MissionTemporalWindowWorker(
            EventoJanelaAvaliacaoService janelaService,
            MissionTemporalWindowEvaluationService evaluationService,
            MissionEvaluationProperties properties,
            ApiObservabilityMetrics metrics
    ) {
        this.janelaService = janelaService;
        this.evaluationService = evaluationService;
        this.properties = properties;
        this.workerId = buildWorkerId();
        metrics.registerMissionTemporalWindowBacklogGauge(this, janelaService::countBacklog);
    }

    @Scheduled(fixedDelayString = "${procel.missions.evaluation.temporal-windows.fixed-delay:5s}")
    public void scheduledPoll() {
        if (!settings().isWorkerEnabled()) {
            return;
        }
        processAvailableBatch();
    }

    public int processAvailableBatch() {
        if (!settings().isWorkerEnabled()) {
            return 0;
        }
        Instant now = Instant.now();
        janelaService.expirarJanelasVencidas(now);
        var claimed = janelaService.claimAvailable(
                settings().getBatchSize(),
                settings().getLeaseDuration(),
                settings().getMaxAttempts()
        );
        int processed = 0;
        for (EventoJanelaWork work : claimed) {
            processClaimed(work, now);
            processed++;
        }
        return processed;
    }

    public void processClaimed(EventoJanelaWork work, Instant now) {
        String outcome;
        if (work.attempts() >= settings().getMaxAttempts()) {
            janelaService.marcarFailed(work.janelaId(), "Temporal window reached max attempts");
            outcome = "failed";
        } else {
            try {
                var result = evaluationService.evaluateClaimed(work.janelaId(), now);
                outcome = result.status();
                if ("skipped".equals(outcome)) {
                    janelaService.marcarRetry(work.janelaId(), now.plus(backoff(work.attempts())), result.reason());
                    outcome = "retry";
                }
            } catch (RuntimeException ex) {
                janelaService.marcarRetry(work.janelaId(), now.plus(backoff(work.attempts())), rootMessage(ex));
                outcome = "retry";
            }
        }
        log.info("application={} event=mission_temporal_window_processed janelaId={} eventoDefinicaoId={} compartimentoId={} status={} attempts={} workerId={}",
                APPLICATION, work.janelaId(), work.eventoDefinicaoId(), work.compartimentoId(), outcome, work.attempts(), workerId);
    }

    private Duration backoff(int attempts) {
        long multiplier = 1L << Math.max(0, attempts - 1);
        long seconds = Math.multiplyExact(settings().getInitialBackoff().toSeconds(), multiplier);
        return Duration.ofSeconds(Math.min(seconds, settings().getMaxBackoff().toSeconds()));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private MissionEvaluationProperties.TemporalWindows settings() {
        return properties.getTemporalWindows();
    }

    private String buildWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception ex) {
            return "api-" + UUID.randomUUID();
        }
    }
}
