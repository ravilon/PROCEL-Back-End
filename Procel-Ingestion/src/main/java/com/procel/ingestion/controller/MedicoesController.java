package com.procel.ingestion.controller;

import com.procel.ingestion.dto.sensors.MedicaoDTOs;
import com.procel.ingestion.service.sensors.MedicoesQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Medicoes", description = "Consulta de medicoes por sensor ou compartimento.")
public class MedicoesController {

    private final MedicoesQueryService service;

    public MedicoesController(MedicoesQueryService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------------
    // SENSOR
    // ---------------------------------------------------------------------

    // GET /api/sensors/{sensorExternalId}/medicoes?from=&to=&limit=
    @GetMapping("/sensors/{sensorExternalId}/medicoes")
    @Operation(summary = "Lista medicoes por sensor", description = "Requer ADMIN, OPERADOR ou ANALISTA. from/to devem ser instantes ISO-8601, por exemplo 2026-03-04T05:00:00Z.")
    @ApiResponse(responseCode = "200", description = "Lista de medicoes retornada.")
    @ApiResponse(responseCode = "400", description = "from/to invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    public ResponseEntity<List<MedicaoDTOs.MedicaoResponse>> listarPorSensor(
            @Parameter(description = "ID externo do sensor.", example = "SII-001") @PathVariable String sensorExternalId,
            @Parameter(description = "Inicio da janela em ISO-8601.", example = "2026-03-04T05:00:00Z") @RequestParam(required = false) String from,
            @Parameter(description = "Fim da janela em ISO-8601.", example = "2026-03-04T05:10:00Z") @RequestParam(required = false) String to,
            @Parameter(description = "Limite de registros.", example = "50") @RequestParam(defaultValue = "200") int limit
    ) {
        Instant fromTs = parseInstantOrNull(from, "from");
        Instant toTs = parseInstantOrNull(to, "to");
        return ResponseEntity.ok(service.listarPorSensor(sensorExternalId, fromTs, toTs, limit));
    }

    // GET /api/sensors/{sensorExternalId}/medicoes/latest
    @GetMapping("/sensors/{sensorExternalId}/medicoes/latest")
    @Operation(summary = "Busca a ultima medicao por sensor", description = "Requer ADMIN, OPERADOR ou ANALISTA.")
    @ApiResponse(responseCode = "200", description = "Ultima medicao retornada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "404", description = "Nenhuma medicao encontrada para o sensor.")
    public ResponseEntity<MedicaoDTOs.MedicaoResponse> latestPorSensor(
            @Parameter(description = "ID externo do sensor.", example = "SII-001") @PathVariable String sensorExternalId
    ) {
        return service.latestPorSensor(sensorExternalId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------------
    // ROOM (Compartimento)
    // ---------------------------------------------------------------------

    // GET /api/rooms/{compartimentoId}/medicoes?from=&to=&limit=
    @GetMapping("/rooms/{compartimentoId}/medicoes")
    @Operation(summary = "Lista medicoes por compartimento", description = "Requer ADMIN, OPERADOR ou ANALISTA. from/to devem ser instantes ISO-8601.")
    @ApiResponse(responseCode = "200", description = "Lista de medicoes retornada.")
    @ApiResponse(responseCode = "400", description = "from/to invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    public ResponseEntity<List<MedicaoDTOs.MedicaoResponse>> listarPorCompartimento(
            @Parameter(description = "ID do compartimento.", example = "2") @PathVariable String compartimentoId,
            @Parameter(description = "Inicio da janela em ISO-8601.", example = "2026-03-04T05:00:00Z") @RequestParam(required = false) String from,
            @Parameter(description = "Fim da janela em ISO-8601.", example = "2026-03-04T05:10:00Z") @RequestParam(required = false) String to,
            @Parameter(description = "Limite de registros.", example = "50") @RequestParam(defaultValue = "200") int limit
    ) {
        Instant fromTs = parseInstantOrNull(from, "from");
        Instant toTs = parseInstantOrNull(to, "to");
        return ResponseEntity.ok(service.listarPorCompartimento(compartimentoId, fromTs, toTs, limit));
    }

    // GET /api/rooms/{compartimentoId}/medicoes/latest
    @GetMapping("/rooms/{compartimentoId}/medicoes/latest")
    @Operation(summary = "Busca a ultima medicao por compartimento", description = "Requer ADMIN, OPERADOR ou ANALISTA.")
    @ApiResponse(responseCode = "200", description = "Ultima medicao retornada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "404", description = "Nenhuma medicao encontrada para o compartimento.")
    public ResponseEntity<MedicaoDTOs.MedicaoResponse> latestPorCompartimento(
            @Parameter(description = "ID do compartimento.", example = "2") @PathVariable String compartimentoId
    ) {
        return service.latestPorCompartimento(compartimentoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Instant parseInstantOrNull(String s, String field) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be ISO-8601 instant, e.g. 2026-03-04T05:00:00Z");
        }
    }
}
