package com.procel.api.service.missions.evaluation;

import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.service.missions.EventoAvaliacaoRequestService;
import com.procel.api.service.missions.EventoAvaliacaoRequestService.EventoAvaliacaoWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class MissionEventEvaluationWorker {
    private static final Logger log = LoggerFactory.getLogger(MissionEventEvaluationWorker.class);
    private static final String APPLICATION = "procel-api";

    private final EventoAvaliacaoRequestService requestService;
    private final MissionEventEvaluationProcessor processor;
    private final MissionEvaluationProperties properties;
    private final ApiObservabilityMetrics metrics;
    private final String workerId;

    public MissionEventEvaluationWorker(
            EventoAvaliacaoRequestService requestService,
            MissionEventEvaluationProcessor processor,
            MissionEvaluationProperties properties,
            ApiObservabilityMetrics metrics
    ) {
        this.requestService = requestService;
        this.processor = processor;
        this.properties = properties;
        this.metrics = metrics;
        this.workerId = buildWorkerId();
        this.metrics.registerMissionBacklogGauge(this, requestService::countBacklog);
    }

    @Scheduled(fixedDelayString = "${procel.missions.evaluation.fixed-delay:5s}")
    public void scheduledPoll() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        processAvailableBatch();
    }

    public int processAvailableBatch() {
        return processAvailableBatch(false);
    }

    public int processAvailableBatch(boolean force) {
        if (!force -and !properties.isWorkerEnabled()) {
            return 0;
        }
        var claimed = requestService.claimAvailable(
                properties.getBatchSize(),
                properties.getLeaseDuration(),
                properties.getMaxAttempts()
        );
        int processed = 0;
        for (EventoAvaliacaoWork work : claimed) {
            processClaimed(work);
            processed++;
        }
        return processed;
    }

    public void processClaimed(EventoAvaliacaoWork work) {
        Instant startedAt = Instant.now();
        String outcome = "completed";
        try {
            var result = processor.process(work, startedAt);
            if (result.ignored()) {
                requestService.markIgnored(work.requestId(), result.reason());
                outcome = "ignored";
            } else {
                outcome = "completed";
            }
        } catch (MissionEventEvaluationFailure ex) {
            outcome = handleFailure(work, ex);
        } catch (DataAccessException ex) {
            outcome = retryOrFail(work, rootMessage(ex));
        } catch (RuntimeException ex) {
            outcome = retryOrFail(work, rootMessage(ex));
        }

        Duration duration = Duration.between(startedAt, Instant.now());
        metrics.missionEvaluationProcessed(outcome, work.attempts(), duration);
        log.info("application={} event=mission_event_evaluation_processed requestId={} medicaoId={} status={} attempts={} durationMs={} workerId={}",
                APPLICATION, work.requestId(), work.medicaoId(), outcome, work.attempts(), duration.toMillis(), workerId);
    }

    private String handleFailure(EventoAvaliacaoWork work, MissionEventEvaluationFailure failure) {
        if (failure.permanent()) {
            requestService.markFailed(work.requestId(), rootMessage(failure));
            return "failed";
        }
        return retryOrFail(work, rootMessage(failure));
    }

    private String retryOrFail(EventoAvaliacaoWork work, String reason) {
        if (work.attempts() >= properties.getMaxAttempts()) {
            requestService.markFailed(work.requestId(), reason);
            return "failed";
        }
        requestService.markRetry(work.requestId(), Instant.now().plus(backoff(work.attempts())), reason);
        return "retry";
    }

    private Duration backoff(int attempts) {
        long multiplier = 1L << Math.max(0, attempts - 1);
        long seconds = Math.multiplyExact(properties.getInitialBackoff().toSeconds(), multiplier);
        return Duration.ofSeconds(Math.min(seconds, properties.getMaxBackoff().toSeconds()));
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

    private String buildWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception ex) {
            return "api-" + UUID.randomUUID();
        }
    }
}
