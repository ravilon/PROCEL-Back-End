package com.procel.api.controller.sensors;

import com.procel.api.dto.sensors.SensorIntegrationSnapshotDTOs;
import com.procel.api.service.sensors.SensorIntegrationSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
