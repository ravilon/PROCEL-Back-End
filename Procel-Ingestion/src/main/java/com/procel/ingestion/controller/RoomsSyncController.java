package com.procel.ingestion.controller;

import com.procel.ingestion.service.rooms.RoomsIngestionResult;
import com.procel.ingestion.service.rooms.RoomsSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@Tag(name = "Rooms", description = "Sincronizacao de campus, predios, unidades e compartimentos.")
public class RoomsSyncController {

    private final RoomsSyncService syncService;

    public RoomsSyncController(RoomsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    @Operation(summary = "Sincroniza salas/compartimentos", description = "Requer ADMIN ou OPERADOR. A fonte depende da configuracao procel.rooms.source.")
    @ApiResponse(responseCode = "200", description = "Sincronizacao executada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    public ResponseEntity<RoomsIngestionResult> sync() {
        RoomsIngestionResult result = syncService.sync();
        return ResponseEntity.ok(result);
    }
}
