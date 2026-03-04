package com.procel.ingestion.controller;

import com.procel.ingestion.dto.sensors.MedicaoDTOs;
import com.procel.ingestion.service.sensors.MedicoesQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
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
    public ResponseEntity<List<MedicaoDTOs.MedicaoResponse>> listarPorSensor(
            @PathVariable String sensorExternalId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "200") int limit
    ) {
        Instant fromTs = parseInstantOrNull(from, "from");
        Instant toTs = parseInstantOrNull(to, "to");
        return ResponseEntity.ok(service.listarPorSensor(sensorExternalId, fromTs, toTs, limit));
    }

    // GET /api/sensors/{sensorExternalId}/medicoes/latest
    @GetMapping("/sensors/{sensorExternalId}/medicoes/latest")
    public ResponseEntity<MedicaoDTOs.MedicaoResponse> latestPorSensor(@PathVariable String sensorExternalId) {
        return service.latestPorSensor(sensorExternalId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------------
    // ROOM (Compartimento)
    // ---------------------------------------------------------------------

    // GET /api/rooms/{compartimentoId}/medicoes?from=&to=&limit=
    @GetMapping("/rooms/{compartimentoId}/medicoes")
    public ResponseEntity<List<MedicaoDTOs.MedicaoResponse>> listarPorCompartimento(
            @PathVariable String compartimentoId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "200") int limit
    ) {
        Instant fromTs = parseInstantOrNull(from, "from");
        Instant toTs = parseInstantOrNull(to, "to");
        return ResponseEntity.ok(service.listarPorCompartimento(compartimentoId, fromTs, toTs, limit));
    }

    // GET /api/rooms/{compartimentoId}/medicoes/latest
    @GetMapping("/rooms/{compartimentoId}/medicoes/latest")
    public ResponseEntity<MedicaoDTOs.MedicaoResponse> latestPorCompartimento(@PathVariable String compartimentoId) {
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