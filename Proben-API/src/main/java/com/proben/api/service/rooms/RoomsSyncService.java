package com.proben.api.service.rooms;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Servico que implementa as operacoes de negocio de rooms sync.
 */
@Service
public class RoomsSyncService {

    private final RoomsSource source;
    private final RoomsIngestionService ingestion;

    public RoomsSyncService(
            @Qualifier("roomsSource") RoomsSource source,
            RoomsIngestionService ingestion
    ) {
        this.source = source;
        this.ingestion = ingestion;
    }

    public RoomsIngestionResult sync() {
        return ingestion.ingest(source.fetchRooms());
    }
}