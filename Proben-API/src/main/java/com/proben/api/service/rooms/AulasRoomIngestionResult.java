package com.proben.api.service.rooms;

record AulasRoomIngestionResult(
        int deleted,
        int inserted,
        int disciplinesCreated,
        int disciplinesUpdated
) {}
