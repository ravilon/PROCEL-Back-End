package com.proben.api.service.rooms;

/**
 * Componente de aulas room ingestion result usado pelo modulo API.
 */

record AulasRoomIngestionResult(
        int deleted,
        int inserted,
        int disciplinesCreated,
        int disciplinesUpdated
) {}
