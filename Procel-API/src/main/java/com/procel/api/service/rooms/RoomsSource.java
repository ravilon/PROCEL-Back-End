package com.procel.api.service.rooms;

import java.util.List;

public interface RoomsSource {
    List<RoomRecord> fetchRooms();
}