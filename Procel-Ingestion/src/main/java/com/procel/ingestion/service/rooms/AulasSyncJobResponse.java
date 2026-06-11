package com.procel.ingestion.service.rooms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AulasSyncJobResponse(
        UUID jobId,
        AulasSyncJobStatus status,
        LocalDate weekStart,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        AulasSyncResult result,
        String error
) {}
