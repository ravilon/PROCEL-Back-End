package com.proben.api.controller.sensors;

import com.proben.api.dto.sensors.SensorIntegrationSnapshotDTOs;
import com.proben.api.service.sensors.SensorIntegrationSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST responsavel pelas operacoes de sensor integration snapshot.
 */
@RestController
@RequestMapping("/api/sensor-integrations")
public class SensorIntegrationSnapshotController {
    private final SensorIntegrationSnapshotService service;

    public SensorIntegrationSnapshotController(SensorIntegrationSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/snapshot")
    public SensorIntegrationSnapshotDTOs.SnapshotResponse snapshot() {
        return service.snapshot();
    }
}
