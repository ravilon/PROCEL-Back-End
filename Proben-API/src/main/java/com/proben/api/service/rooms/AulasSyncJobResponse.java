package com.proben.api.service.rooms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Componente de aulas sync job response usado pelo modulo API.
 */

public record AulasSyncJobResponse(
        UUID jobId,
        AulasSyncJobStatus status,
        LocalDate weekStart,
        String roomId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        AulasSyncProgress progress,
        AulasSyncResult result,
        String error
) {}
