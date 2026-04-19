package com.procel.ingestion.controller;

import com.procel.ingestion.service.sensors.MockIngestRequest;
import com.procel.ingestion.service.sensors.MockIngestResponse;
import com.procel.ingestion.service.sensors.SensorsMockIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensors")
@Tag(name = "Sensors", description = "Seed e ingestao mockada de sensores.")
public class SensorsMockIngestController {

    private final SensorsMockIngestService service;

    public SensorsMockIngestController(SensorsMockIngestService service) {
        this.service = service;
    }

    @PostMapping("/ingest/mock")
    @Operation(summary = "Gera medicoes mockadas", description = "Requer ADMIN ou INGESTOR. Gera medicoes e valores para um sensor cadastrado.")
    @ApiResponse(responseCode = "200", description = "Ingestao mockada executada.")
    @ApiResponse(responseCode = "400", description = "Parametros invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "404", description = "Sensor nao encontrado.")
    public ResponseEntity<MockIngestResponse> ingestMock(@RequestBody(required = false) MockIngestRequest req) {
        return ResponseEntity.ok(service.ingest(req));
    }
}
