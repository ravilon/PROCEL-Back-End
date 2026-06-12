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
    private final Map<JobKey, UUID> activeJobs = new ConcurrentHashMap<>();

    public AulasSyncJobService(
            AulasSyncService syncService,
            @Qualifier("aulasSyncTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.syncService = syncService;
        this.taskExecutor = taskExecutor;
    }

    public synchronized AulasSyncJobResponse start(LocalDate date, String roomId) {
        LocalDate weekStart = normalizeWeekStart(date);
        String normalizedRoomId = normalizeRoomId(roomId);
        JobKey key = new JobKey(weekStart, normalizedRoomId);
        JobKey conflictingKey = findConflictingActiveJob(weekStart, normalizedRoomId);
        UUID activeJobId = conflictingKey == null ? null : activeJobs.get(conflictingKey);

        if (activeJobId != null) {
            JobState activeJob = jobs.get(activeJobId);
            if (activeJob != null && activeJob.isActive()) {
                return activeJob.snapshot();
            }
            activeJobs.remove(conflictingKey, activeJobId);
        }

        JobState job = new JobState(UUID.randomUUID(), weekStart, normalizedRoomId);
        jobs.put(job.jobId, job);
        activeJobs.put(key, job.jobId);

        try {
            taskExecutor.execute(() -> execute(job));
        } catch (RuntimeException ex) {
            job.fail(rootMessage(ex));
            activeJobs.remove(key, job.jobId);
        }

        return job.snapshot();
    }

    private JobKey findConflictingActiveJob(LocalDate weekStart, String roomId) {
        for (JobKey activeKey : activeJobs.keySet()) {
            if (!activeKey.weekStart().equals(weekStart)) {
                continue;
            }
            if (roomId == null || activeKey.roomId() == null || roomId.equals(activeKey.roomId())) {
                return activeKey;
            }
        }
        return null;
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
            job.complete(syncService.sync(job.weekStart, job.roomId, job::updateProgress));
        } catch (Exception ex) {
            job.fail(rootMessage(ex));
        } finally {
            activeJobs.remove(new JobKey(job.weekStart, job.roomId), job.jobId);
        }
    }

    private LocalDate normalizeWeekStart(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("weekStart is required");
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    private String normalizeRoomId(String roomId) {
        return roomId == null || roomId.isBlank() ? null : roomId.trim();
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
        private final String roomId;
        private final Instant createdAt = Instant.now();
        private volatile AulasSyncJobStatus status = AulasSyncJobStatus.PENDING;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile AulasSyncProgress progress = AulasSyncProgress.pending();
        private volatile AulasSyncResult result;
        private volatile String error;

        private JobState(UUID jobId, LocalDate weekStart, String roomId) {
            this.jobId = jobId;
            this.weekStart = weekStart;
            this.roomId = roomId;
        }

        private void start() {
            startedAt = Instant.now();
            status = AulasSyncJobStatus.RUNNING;
        }

        private void complete(AulasSyncResult result) {
            this.result = result;
            this.progress = AulasSyncProgress.of(
                    result.roomsRequested(),
                    result.roomsRequested(),
                    result.roomsSynced(),
                    result.roomsFailed()
            );
            completedAt = Instant.now();
            status = AulasSyncJobStatus.COMPLETED;
        }

        private void updateProgress(AulasSyncProgress progress) {
            this.progress = progress;
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
                    roomId,
                    createdAt,
                    startedAt,
                    completedAt,
                    progress,
                    result,
                    error
            );
        }
    }

    private record JobKey(LocalDate weekStart, String roomId) {}
}
