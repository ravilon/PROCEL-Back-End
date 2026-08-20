package com.procel.api.service.analytics;

import com.procel.api.config.AnalyticsAggregationProperties;
import com.procel.api.observability.ApiObservabilityMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AggregationJobWorker {
    private static final Logger log = LoggerFactory.getLogger(AggregationJobWorker.class);
    private static final String APPLICATION = "procel-api";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AggregationWindowProcessor processor;
    private final AnalyticsAggregationProperties properties;
    private final ApiObservabilityMetrics metrics;
    private final String workerId;

    public AggregationJobWorker(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            AggregationWindowProcessor processor,
            AnalyticsAggregationProperties properties,
            ApiObservabilityMetrics metrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.processor = processor;
        this.properties = properties;
        this.metrics = metrics;
        this.workerId = buildWorkerId();
    }

    @Scheduled(fixedDelayString = "${procel.analytics.aggregation.poll-interval:5s}")
    public void scheduledPoll() {
        if (!properties.isWorkerEnabled()) {
            return;
        }
        processAvailableBatch();
    }

    public int processAvailableBatch() {
        if (!properties.isWorkerEnabled()) {
            return 0;
        }
        recoverExpiredWindowsAtAttemptLimit();
        int processed = 0;
        for (int i = 0; i < properties.getBatchSize(); i++) {
            boolean didWork = processOneAvailableWindow();
            if (!didWork) {
                break;
            }
            processed++;
        }
        return processed;
    }

    public boolean processOneAvailableWindow() {
        AggregationWindowWork work = claimNextWindow();
        if (work == null) {
            return false;
        }
        Instant startedAt = Instant.now();
        String outcome = "completed";
        try {
            processor.process(work);
            completeWindow(work.windowId());
        } catch (Exception ex) {
            failWindow(work, rootMessage(ex));
            outcome = work.attempts() >= properties.getMaxAttempts() ? "failed" : "retry";
        }
        Duration duration = Duration.between(startedAt, Instant.now());
        metrics.windowProcessed(outcome, work.attempts(), duration);
        log.info("application={} event=aggregation_window_processed aggregationJobId={} aggregationWindowId={} status={} attempts={} durationMs={}",
                APPLICATION, work.jobId(), work.windowId(), outcome, work.attempts(), duration.toMillis());
        updateJobProgress(work.jobId());
        return true;
    }

    private AggregationWindowWork claimNextWindow() {
        return transactionTemplate.execute(status -> {
            List<AggregationWindowWork> claimed = jdbcTemplate.query("""
                    update analytics_aggregation_window aw
                    set status = 'PROCESSING',
                        attempts = aw.attempts + 1,
                        locked_at = now(),
                        locked_by = ?,
                        started_at = coalesce(aw.started_at, now()),
                        completed_at = null,
                        error = null
                    from analytics_aggregation_job job
                    where aw.id = (
                        select candidate.id
                        from analytics_aggregation_window candidate
                        join analytics_aggregation_job candidate_job on candidate_job.id = candidate.job_id
                        where candidate_job.status in ('PENDING', 'PROCESSING')
                          and (
                                (
                                    candidate.status = 'PENDING'
                                    and candidate.next_attempt_at <= now()
                                    and candidate.attempts < ?
                                )
                                or (
                                    candidate.status = 'PROCESSING'
                                    and candidate.locked_at < now() - (? * interval '1 second')
                                    and candidate.attempts < ?
                                )
                            )
                        order by candidate.next_attempt_at asc, candidate.window_index asc
                        for update of candidate skip locked
                        limit 1
                    )
                    and job.id = aw.job_id
                    returning aw.id, aw.job_id, aw.window_index, aw.window_from, aw.window_to,
                              job.sensor_external_id, job.compartimento_id, aw.attempts
                    """, (rs, rowNum) -> new AggregationWindowWork(
                    rs.getObject("id", UUID.class),
                    rs.getObject("job_id", UUID.class),
                    rs.getInt("window_index"),
                    rs.getTimestamp("window_from").toInstant(),
                    rs.getTimestamp("window_to").toInstant(),
                    rs.getString("sensor_external_id"),
                    rs.getString("compartimento_id"),
                    rs.getInt("attempts")
            ), workerId, properties.getMaxAttempts(), properties.getLeaseTimeout().toSeconds(), properties.getMaxAttempts());
            if (claimed.isEmpty()) {
                return null;
            }
            AggregationWindowWork work = claimed.getFirst();
            jdbcTemplate.update("""
                    update analytics_aggregation_job
                    set status = 'PROCESSING',
                        started_at = coalesce(started_at, now()),
                        processing_windows = (
                            select count(*) from analytics_aggregation_window
                            where job_id = ? and status = 'PROCESSING'
                        )
                    where id = ? and status in ('PENDING', 'PROCESSING')
                    """, work.jobId(), work.jobId());
            return work;
        });
    }

    private void completeWindow(UUID windowId) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                update analytics_aggregation_window
                set status = 'COMPLETED',
                    completed_at = now(),
                    locked_at = null,
                    locked_by = null,
                    next_attempt_at = now(),
                    error = null
                where id = ?
                """, windowId));
    }

    private void failWindow(AggregationWindowWork work, String error) {
        transactionTemplate.executeWithoutResult(status -> {
            if (work.attempts() >= properties.getMaxAttempts()) {
                jdbcTemplate.update("""
                        update analytics_aggregation_window
                        set status = 'FAILED',
                            completed_at = now(),
                            locked_at = null,
                            locked_by = null,
                            error = ?
                        where id = ?
                        """, error, work.windowId());
            } else {
                jdbcTemplate.update("""
                        update analytics_aggregation_window
                        set status = 'PENDING',
                            locked_at = null,
                            locked_by = null,
                            next_attempt_at = now() + (? * interval '1 second'),
                            error = ?
                        where id = ?
                        """, backoffSeconds(work.attempts()), error, work.windowId());
            }
        });
    }

    public int recoverExpiredWindowsAtAttemptLimit() {
        int updated = jdbcTemplate.update("""
                update analytics_aggregation_window
                set status = 'FAILED',
                    completed_at = now(),
                    locked_at = null,
                    locked_by = null,
                    error = coalesce(error, 'Lease expired after max attempts')
                where status = 'PROCESSING'
                  and locked_at < now() - (? * interval '1 second')
                  and attempts >= ?
                """, properties.getLeaseTimeout().toSeconds(), properties.getMaxAttempts());
        if (updated > 0) {
            List<UUID> jobs = jdbcTemplate.query("""
                    select distinct job_id
                    from analytics_aggregation_window
                    where status = 'FAILED'
                    """, (rs, rowNum) -> rs.getObject("job_id", UUID.class));
            jobs.forEach(this::updateJobProgress);
        }
        return updated;
    }

    public void updateJobProgress(UUID jobId) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                update analytics_aggregation_job job
                set completed_windows = stats.completed_count,
                    failed_windows = stats.failed_count,
                    processing_windows = stats.processing_count,
                    status = case
                        when stats.processing_count > 0 then 'PROCESSING'
                        when stats.failed_count > 0 and stats.pending_count = 0 then 'FAILED'
                        when stats.completed_count = job.total_windows then 'COMPLETED'
                        when job.started_at is not null then 'PROCESSING'
                        else 'PENDING'
                    end,
                    completed_at = case
                        when (stats.failed_count > 0 and stats.pending_count = 0 and stats.processing_count = 0)
                            or stats.completed_count = job.total_windows
                        then coalesce(job.completed_at, now())
                        else null
                    end,
                    error = case
                        when stats.failed_count > 0 and stats.pending_count = 0 and stats.processing_count = 0
                        then (
                            select error from analytics_aggregation_window
                            where job_id = job.id and status = 'FAILED'
                            order by window_index asc
                            limit 1
                        )
                        else null
                    end
                from (
                    select
                        count(*) filter (where status = 'COMPLETED')::int as completed_count,
                        count(*) filter (where status = 'FAILED')::int as failed_count,
                        count(*) filter (where status = 'PROCESSING')::int as processing_count,
                        count(*) filter (where status = 'PENDING')::int as pending_count
                    from analytics_aggregation_window
                    where job_id = ?
                ) stats
                where job.id = ?
                """, jobId, jobId));
    }

    private long backoffSeconds(int attempts) {
        long multiplier = 1L << Math.max(0, attempts - 1);
        return Math.multiplyExact(properties.getBackoff().toSeconds(), multiplier);
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
