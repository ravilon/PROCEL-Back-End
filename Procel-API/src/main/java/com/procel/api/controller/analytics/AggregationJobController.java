package com.procel.api.controller.analytics;

import com.procel.api.dto.analytics.AggregationJobDTOs;
import com.procel.api.service.analytics.AggregationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/aggregation-jobs")
@Tag(name = "Analytics", description = "Orquestracao assincrona de agregacoes por periodo.")
public class AggregationJobController {
    private final AggregationJobService service;

    public AggregationJobController(AggregationJobService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria um job de agregacao por periodo")
    @ApiResponse(responseCode = "202", description = "Job criado ou job equivalente existente retornado.")
    @ApiResponse(responseCode = "400", description = "Periodo ou janela invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "422", description = "Sensor ou compartimento inexistente.")
    public ResponseEntity<AggregationJobDTOs.AggregationJobResponse> create(
            @Valid @RequestBody AggregationJobDTOs.CreateAggregationJobRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.accepted().body(service.create(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta o estado de um job de agregacao")
    @ApiResponse(responseCode = "200", description = "Estado atual do job.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "404", description = "Job inexistente.")
    public ResponseEntity<AggregationJobDTOs.AggregationJobResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }
}
