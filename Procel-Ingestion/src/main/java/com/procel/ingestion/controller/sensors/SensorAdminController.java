package com.procel.ingestion.controller.sensors;

import com.procel.ingestion.dto.sensors.SensorAdminDTOs;
import com.procel.ingestion.service.sensors.SensorAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public List<SensorAdminDTOs.TipoSensorResponse> listarTipos() {
        return service.listarTipos();
    }

    @PostMapping("/types")
    @Operation(summary = "Cria tipo de sensor")
    public SensorAdminDTOs.TipoSensorResponse criarTipo(
            @RequestBody SensorAdminDTOs.TipoSensorRequest request
    ) {
        return service.criarTipo(request);
    }

    @PostMapping("/types/{tipoNome}/parameters")
    @Operation(summary = "Cria parametro para um tipo de sensor")
    public SensorAdminDTOs.ParametroResponse criarParametro(
            @PathVariable String tipoNome,
            @RequestBody SensorAdminDTOs.ParametroRequest request
    ) {
        return service.criarParametro(tipoNome, request);
    }

    @PostMapping("/sensors")
    @Operation(summary = "Cadastra sensor em um compartimento")
    public SensorAdminDTOs.SensorResponse criarSensor(
            @RequestBody SensorAdminDTOs.SensorRequest request
    ) {
        return service.criarSensor(request);
    }
}
