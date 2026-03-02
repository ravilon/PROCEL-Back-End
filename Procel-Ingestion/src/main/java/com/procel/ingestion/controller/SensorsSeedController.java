package com.procel.ingestion.controller;

import com.procel.ingestion.service.sensors.SensorsSeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensors")
public class SensorsSeedController {

    private final SensorsSeedService service;

    public SensorsSeedController(SensorsSeedService service) {
        this.service = service;
    }

    @PostMapping("/seed/from-resource")
    public ResponseEntity<String> seedFromResource() {
        var result = service.seedFromResource();
        return ResponseEntity.ok(result);
    }
}