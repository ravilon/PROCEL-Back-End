package com.procel.ingestion.service.rooms;

import java.util.List;

public interface RoomsSource {
    List<RoomRecord> fetchRooms();
}