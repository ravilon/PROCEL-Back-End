package com.procel.ingestion.controller;

import com.procel.ingestion.dto.sensors.RegraDTOs;
import com.procel.ingestion.service.sensors.RegrasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rules")
@Tag(name = "Rules", description = "Cadastro de grupos de regras, regras por parametro e vinculos com sensores.")
public class RegrasController {

    private final RegrasService service;

    public RegrasController(RegrasService service) {
        this.service = service;
    }

    @PostMapping("/groups")
    @Operation(summary = "Cria grupo de regras", description = "Requer ADMIN ou OPERADOR.")
    @ApiResponse(responseCode = "200", description = "Grupo criado.")
    public RegraDTOs.GrupoRegraResponse criarGrupo(@RequestBody RegraDTOs.GrupoRegraRequest req) {
        return service.criarGrupo(req);
    }

    @GetMapping("/groups")
    @Operation(summary = "Lista grupos de regras", description = "Requer ADMIN ou OPERADOR.")
    public List<RegraDTOs.GrupoRegraResponse> listarGrupos() {
        return service.listarGrupos();
    }

    @GetMapping("/parameter-defs")
    @Operation(summary = "Lista parametros definidos por tipo de sensor", description = "Requer ADMIN ou OPERADOR.")
    public List<RegraDTOs.ParametroDefResponse> listarParametros(@RequestParam String tipoNome) {
        return service.listarParametros(tipoNome);
    }

    @PostMapping("/groups/{grupoId}/rules")
    @Operation(summary = "Cria regra para parametro em um grupo", description = "Requer ADMIN ou OPERADOR.")
    public RegraDTOs.RegraParametroResponse criarRegra(
            @PathVariable UUID grupoId,
            @RequestBody RegraDTOs.RegraParametroRequest req
    ) {
        return service.criarRegra(grupoId, req);
    }

    @GetMapping("/groups/{grupoId}/rules")
    @Operation(summary = "Lista regras de um grupo", description = "Requer ADMIN ou OPERADOR.")
    public List<RegraDTOs.RegraParametroResponse> listarRegras(@PathVariable UUID grupoId) {
        return service.listarRegras(grupoId);
    }

    @PostMapping("/sensors/{sensorExternalId}/groups")
    @Operation(summary = "Vincula grupo de regras a um sensor", description = "Requer ADMIN ou OPERADOR.")
    public RegraDTOs.SensorGrupoRegraResponse vincularGrupoAoSensor(
            @PathVariable String sensorExternalId,
            @RequestBody RegraDTOs.SensorGrupoRegraRequest req
    ) {
        return service.vincularGrupoAoSensor(sensorExternalId, req);
    }

    @GetMapping("/sensors/{sensorExternalId}/groups")
    @Operation(summary = "Lista vinculos de grupos de regras de um sensor", description = "Requer ADMIN ou OPERADOR.")
    public List<RegraDTOs.SensorGrupoRegraResponse> listarVinculosDoSensor(@PathVariable String sensorExternalId) {
        return service.listarVinculosDoSensor(sensorExternalId);
    }
}
