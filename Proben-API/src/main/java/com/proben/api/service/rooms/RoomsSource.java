package com.proben.api.service.rooms;

import java.util.List;

public interface RoomsSource {
    List<RoomRecord> fetchRooms();
}