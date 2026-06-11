package com.procel.ingestion.service.rooms;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AulasSyncJobService {

    private final AulasSyncService syncService;
    private final TaskExecutor taskExecutor;
    private final Map<UUID, JobState> jobs = new ConcurrentHashMap<>();
    private final Map<LocalDate, UUID> activeJobsByWeek = new ConcurrentHashMap<>();

    public AulasSyncJobService(
            AulasSyncService syncService,
            @Qualifier("aulasSyncTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.syncService = syncService;
        this.taskExecutor = taskExecutor;
    }

    public synchronized AulasSyncJobResponse start(LocalDate date) {
        LocalDate weekStart = normalizeWeekStart(date);
        UUID activeJobId = activeJobsByWeek.get(weekStart);

        if (activeJobId != null) {
            JobState activeJob = jobs.get(activeJobId);
            if (activeJob != null && activeJob.isActive()) {
                return activeJob.snapshot();
            }
            activeJobsByWeek.remove(weekStart, activeJobId);
        }

        JobState job = new JobState(UUID.randomUUID(), weekStart);
        jobs.put(job.jobId, job);
        activeJobsByWeek.put(weekStart, job.jobId);

        try {
            taskExecutor.execute(() -> execute(job));
        } catch (RuntimeException ex) {
            job.fail(rootMessage(ex));
            activeJobsByWeek.remove(weekStart, job.jobId);
        }

        return job.snapshot();
    }

    public AulasSyncJobResponse get(UUID jobId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Class schedule sync job not found id=" + jobId);
        }
        return job.snapshot();
    }

    private void execute(JobState job) {
        job.start();
        try {
            job.complete(syncService.syncAll(job.weekStart));
        } catch (Exception ex) {
            job.fail(rootMessage(ex));
        } finally {
            activeJobsByWeek.remove(job.weekStart, job.jobId);
        }
    }

    private LocalDate normalizeWeekStart(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("weekStart is required");
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null
                ? current.getMessage()
                : current.getClass().getSimpleName();
    }

    private static final class JobState {
        private final UUID jobId;
        private final LocalDate weekStart;
        private final Instant createdAt = Instant.now();
        private volatile AulasSyncJobStatus status = AulasSyncJobStatus.PENDING;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile AulasSyncResult result;
        private volatile String error;

        private JobState(UUID jobId, LocalDate weekStart) {
            this.jobId = jobId;
            this.weekStart = weekStart;
        }

        private void start() {
            startedAt = Instant.now();
            status = AulasSyncJobStatus.RUNNING;
        }

        private void complete(AulasSyncResult result) {
            this.result = result;
            completedAt = Instant.now();
            status = AulasSyncJobStatus.COMPLETED;
        }

        private void fail(String error) {
            this.error = error;
            completedAt = Instant.now();
            status = AulasSyncJobStatus.FAILED;
        }

        private boolean isActive() {
            return status == AulasSyncJobStatus.PENDING ||
                    status == AulasSyncJobStatus.RUNNING;
        }

        private AulasSyncJobResponse snapshot() {
            return new AulasSyncJobResponse(
                    jobId,
                    status,
                    weekStart,
                    createdAt,
                    startedAt,
                    completedAt,
                    result,
                    error
            );
        }
    }
}
