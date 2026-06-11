package com.procel.ingestion.service.rooms;

record AulasRoomIngestionResult(
        int deleted,
        int inserted,
        int disciplinesCreated,
        int disciplinesUpdated
) {}
