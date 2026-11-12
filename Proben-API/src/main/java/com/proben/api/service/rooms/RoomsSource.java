package com.proben.api.service.rooms;

import java.util.List;

/**
 * Componente de rooms source usado pelo modulo API.
 */

public interface RoomsSource {
    List<RoomRecord> fetchRooms();
}