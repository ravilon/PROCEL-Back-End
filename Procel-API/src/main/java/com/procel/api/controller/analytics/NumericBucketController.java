package com.procel.api.controller.analytics;

import com.procel.api.dto.analytics.NumericBucketDTOs;
import com.procel.api.service.analytics.NumericBucketQuery;
import com.procel.api.service.analytics.NumericBucketQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/numeric-buckets")
@Tag(name = "Analytics", description = "Consulta de buckets numericos persistidos.")
public class NumericBucketController {
    private final NumericBucketQueryService service;

    public NumericBucketController(NumericBucketQueryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista buckets numericos persistidos")
    @ApiResponse(responseCode = "200", description = "Buckets paginados.")
    @ApiResponse(responseCode = "400", description = "Filtros invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "422", description = "Sensor, parametro ou compartimento inexistente.")
    public ResponseEntity<NumericBucketDTOs.NumericBucketPage> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String sensorExternalId,
            @RequestParam(required = false) UUID parametroDefId,
            @RequestParam(required = false) String compartimentoId,
            @RequestParam(required = false) Integer aggregationVersion,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(service.list(query(from, to, sensorExternalId, parametroDefId,
                compartimentoId, aggregationVersion, page, size)));
    }

    @GetMapping("/summary")
    @Operation(summary = "Consolida buckets numericos persistidos")
    @ApiResponse(responseCode = "200", description = "Resumo consolidado por sensor, parametro, compartimento e versao.")
    @ApiResponse(responseCode = "400", description = "Filtros invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "422", description = "Sensor, parametro ou compartimento inexistente.")
    public ResponseEntity<List<NumericBucketDTOs.NumericBucketSummaryResponse>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String sensorExternalId,
            @RequestParam(required = false) UUID parametroDefId,
            @RequestParam(required = false) String compartimentoId,
            @RequestParam(required = false) Integer aggregationVersion
    ) {
        return ResponseEntity.ok(service.summary(query(from, to, sensorExternalId, parametroDefId,
                compartimentoId, aggregationVersion, null, null)));
    }

    private NumericBucketQuery query(
            Instant from,
            Instant to,
            String sensorExternalId,
            UUID parametroDefId,
            String compartimentoId,
            Integer aggregationVersion,
            Integer page,
            Integer size
    ) {
        return new NumericBucketQuery(
                from,
                to,
                normalize(sensorExternalId),
                parametroDefId,
                normalize(compartimentoId),
                aggregationVersion,
                page,
                size
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
