package com.procel.ingestion.controller;

import com.procel.ingestion.service.rooms.RoomsIngestionResult;
import com.procel.ingestion.service.rooms.RoomsSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomsSyncController {

    private final RoomsSyncService syncService;

    public RoomsSyncController(RoomsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<RoomsIngestionResult> sync() {
        RoomsIngestionResult result = syncService.sync();
        return ResponseEntity.ok(result);
    }
}