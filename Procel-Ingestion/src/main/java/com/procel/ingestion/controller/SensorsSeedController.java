package com.procel.ingestion.controller;

import com.procel.ingestion.service.sensors.SensorsSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensors")
@Tag(name = "Sensors", description = "Seed e ingestao mockada de sensores.")
public class SensorsSeedController {

    private final SensorsSeedService service;

    public SensorsSeedController(SensorsSeedService service) {
        this.service = service;
    }

    @PostMapping("/seed/from-resource")
    @Operation(summary = "Carrega sensores do resource seed", description = "Requer ADMIN. Usa o arquivo configurado em procel.sensors.seed-path.")
    @ApiResponse(responseCode = "200", description = "Seed executado.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    public ResponseEntity<String> seedFromResource() {
        var result = service.seedFromResource();
        return ResponseEntity.ok(result);
    }
}
