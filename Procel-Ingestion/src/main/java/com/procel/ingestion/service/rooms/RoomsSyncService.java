package com.procel.ingestion.service.rooms;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RoomsSyncService {

    private final RoomsSource source;
    private final RoomsIngestionService ingestion;

    public RoomsSyncService(@Qualifier("roomsSource") RoomsSource source,
                            RoomsIngestionService ingestion) {
        this.source = source;
        this.ingestion = ingestion;
    }

    public int sync() {
        return ingestion.ingest(source.fetchRooms());
    }
}