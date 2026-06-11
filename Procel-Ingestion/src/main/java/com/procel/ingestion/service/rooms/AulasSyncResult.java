package com.procel.ingestion.service.rooms;

import java.time.LocalDate;
import java.util.List;

public record AulasSyncResult(
        LocalDate weekStart,
        LocalDate weekEnd,
        int roomsRequested,
        int roomsSynced,
        int roomsFailed,
        int occurrencesFetched,
        int occurrencesDeleted,
        int occurrencesInserted,
        int disciplinesCreated,
        int disciplinesUpdated,
        long elapsedMs,
        List<String> errors
) {}
