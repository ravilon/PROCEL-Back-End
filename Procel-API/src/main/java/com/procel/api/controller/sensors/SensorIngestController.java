package com.procel.api.controller.sensors;

import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.service.sensors.SensorIngestOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensors")
@Tag(name = "Sensors", description = "Ingestao canonica de medicoes de sensores.")
public class SensorIngestController {
    private final SensorIngestOrchestrator orchestrator;

    public SensorIngestController(SensorIngestOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/ingest")
    @Operation(summary = "Ingere medicao canonica", description = "Requer ADMIN ou INGESTOR.")
    @ApiResponse(responseCode = "201", description = "Medicao criada.")
    @ApiResponse(responseCode = "200", description = "Mensagem duplicada equivalente.")
    @ApiResponse(responseCode = "409", description = "Mesma chave idempotente com payload divergente.")
    public ResponseEntity<SensorIngestDTOs.CanonicalIngestResponse> ingest(
            Authentication authentication,
            @Valid @RequestBody SensorIngestDTOs.CanonicalIngestRequest request
    ) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ApiStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Token ausente ou invalido"
            );
        }
        var outcome = orchestrator.ingest(authentication.getName(), request);
        return ResponseEntity.status(outcome.status()).body(outcome.response());
    }
}
