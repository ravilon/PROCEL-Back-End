package com.procel.ingestion.controller.sensors;

import com.procel.ingestion.dto.sensors.SensorAdminDTOs;
import com.procel.ingestion.service.sensors.SensorAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/sensor-admin")
@PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
@Tag(name = "Sensor administration", description = "Cadastro de sensores, tipos e parametros.")
public class SensorAdminController {

    private final SensorAdminService service;

    public SensorAdminController(SensorAdminService service) {
        this.service = service;
    }

    @GetMapping("/types")
    @Operation(summary = "Lista tipos de sensor com seus parametros")
    public List<SensorAdminDTOs.TipoSensorResponse> listarTipos(
            @RequestParam(defaultValue = "false") boolean includeHidden
    ) {
        return service.listarTipos(includeHidden);
    }

    @PostMapping("/types")
    @Operation(summary = "Cria tipo de sensor")
    public SensorAdminDTOs.TipoSensorResponse criarTipo(
            @RequestBody SensorAdminDTOs.TipoSensorRequest request
    ) {
        return service.criarTipo(request);
    }

    @PutMapping("/types/{tipoNome}")
    @Operation(summary = "Renomeia tipo de sensor preservando sensores e parametros associados")
    public SensorAdminDTOs.TipoSensorResponse atualizarTipo(
            @PathVariable String tipoNome,
            @RequestBody SensorAdminDTOs.TipoSensorUpdateRequest request
    ) {
        return service.atualizarTipo(tipoNome, request);
    }

    @PostMapping("/types/{tipoNome}/parameters")
    @Operation(summary = "Cria parametro para um tipo de sensor")
    public SensorAdminDTOs.ParametroResponse criarParametro(
            @PathVariable String tipoNome,
            @RequestBody SensorAdminDTOs.ParametroRequest request
    ) {
        return service.criarParametro(tipoNome, request);
    }

    @PutMapping("/parameters/{parametroId}")
    @Operation(summary = "Atualiza parametro de um tipo de sensor")
    public SensorAdminDTOs.ParametroResponse atualizarParametro(
            @PathVariable java.util.UUID parametroId,
            @RequestBody SensorAdminDTOs.ParametroRequest request
    ) {
        return service.atualizarParametro(parametroId, request);
    }

    @DeleteMapping("/parameters/{parametroId}")
    @Operation(summary = "Oculta parametro preservando historico e regras")
    public void ocultarParametro(@PathVariable java.util.UUID parametroId) {
        service.ocultarParametro(parametroId);
    }

    @PostMapping("/parameters/{parametroId}/restore")
    @Operation(summary = "Reativa parametro oculto")
    public SensorAdminDTOs.ParametroResponse reativarParametro(
            @PathVariable java.util.UUID parametroId
    ) {
        return service.reativarParametro(parametroId);
    }

    @PostMapping("/sensors")
    @Operation(summary = "Cadastra sensor em um compartimento")
    public SensorAdminDTOs.SensorResponse criarSensor(
            @RequestBody SensorAdminDTOs.SensorRequest request
    ) {
        return service.criarSensor(request);
    }

    @DeleteMapping("/sensors/{externalId}")
    @Operation(summary = "Oculta sensor preservando medicoes e vinculos")
    public void ocultarSensor(@PathVariable String externalId) {
        service.ocultarSensor(externalId);
    }

    @PostMapping("/sensors/{externalId}/restore")
    @Operation(summary = "Reativa sensor oculto")
    public SensorAdminDTOs.SensorResponse reativarSensor(@PathVariable String externalId) {
        return service.reativarSensor(externalId);
    }
}
