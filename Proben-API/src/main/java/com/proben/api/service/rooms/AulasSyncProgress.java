package com.proben.api.service.rooms;

/**
 * Contadores e percentual de avanço de uma sincronização de aulas.
 *
 * @param roomsTotal total de salas previstas no job
 * @param roomsProcessed salas já processadas, com ou sem erro
 * @param roomsSynced salas sincronizadas com sucesso
 * @param roomsFailed salas cuja sincronização falhou
 * @param progressPercent percentual concluído entre zero e cem
 */

public record AulasSyncProgress(
        int roomsTotal,
        int roomsProcessed,
        int roomsSynced,
        int roomsFailed,
        int progressPercent
) {
    /** Cria o estado inicial de uma sincronização ainda não processada. */
    public static AulasSyncProgress pending() {
        return new AulasSyncProgress(0, 0, 0, 0, 0);
    }

    /** Calcula o percentual concluído a partir dos contadores do job. */
    public static AulasSyncProgress of(
            int roomsTotal,
            int roomsProcessed,
            int roomsSynced,
            int roomsFailed
    ) {
        int percent = roomsTotal == 0
                ? 100
                : Math.min(100, (int) Math.round((roomsProcessed * 100.0) / roomsTotal));
        return new AulasSyncProgress(
                roomsTotal,
                roomsProcessed,
                roomsSynced,
                roomsFailed,
                percent
        );
    }
}
