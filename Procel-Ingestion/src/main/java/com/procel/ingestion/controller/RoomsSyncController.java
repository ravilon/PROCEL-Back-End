package com.procel.ingestion.controller;

import com.procel.ingestion.service.rooms.RoomsSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomsSyncController {

    private final RoomsSyncService syncService;

    public RoomsSyncController(RoomsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        int processed = syncService.sync();
        return ResponseEntity.ok(Map.of("compartimentosProcessed", processed));
    }
}