package com.procel.ingestion.service.rooms;

public record AulasSyncProgress(
        int roomsTotal,
        int roomsProcessed,
        int roomsSynced,
        int roomsFailed,
        int progressPercent
) {
    public static AulasSyncProgress pending() {
        return new AulasSyncProgress(0, 0, 0, 0, 0);
    }

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
