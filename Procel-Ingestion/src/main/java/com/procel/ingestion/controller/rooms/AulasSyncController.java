package com.procel.ingestion.controller.rooms;

import com.procel.ingestion.service.rooms.AulasSyncJobResponse;
import com.procel.ingestion.service.rooms.AulasSyncJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/aulas")
@Tag(name = "Rooms", description = "Sincronizacao de salas e suas aulas.")
public class AulasSyncController {

    private final AulasSyncJobService jobService;

    public AulasSyncController(AulasSyncJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/sync")
    @Operation(
            summary = "Sincroniza aulas por sala",
            description = "Inicia assincronamente a sincronizacao de todas as salas para a semana informada."
    )
    @ApiResponse(responseCode = "202", description = "Sincronizacao iniciada ou ja em execucao.")
    @ApiResponse(responseCode = "400", description = "Data invalida.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    public ResponseEntity<AulasSyncJobResponse> sync(
            @Parameter(description = "Data pertencente a semana desejada.", example = "2026-06-14")
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart
    ) {
        return ResponseEntity.accepted().body(jobService.start(weekStart));
    }

    @GetMapping("/sync/{jobId}")
    @Operation(summary = "Consulta o estado de uma sincronizacao de aulas")
    @ApiResponse(responseCode = "200", description = "Estado atual do job.")
    @ApiResponse(responseCode = "400", description = "Job inexistente.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    public ResponseEntity<AulasSyncJobResponse> getJob(
            @Parameter(description = "Identificador retornado ao iniciar o sync.")
            @PathVariable
            UUID jobId
    ) {
        return ResponseEntity.ok(jobService.get(jobId));
    }
}
