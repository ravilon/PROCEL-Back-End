package com.procel.ingestion.controller;

import com.procel.ingestion.service.sensors.MockIngestRequest;
import com.procel.ingestion.service.sensors.MockIngestResponse;
import com.procel.ingestion.service.sensors.SensorsMockIngestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensors")
public class SensorsMockIngestController {

    private final SensorsMockIngestService service;

    public SensorsMockIngestController(SensorsMockIngestService service) {
        this.service = service;
    }

    @PostMapping("/ingest/mock")
    public ResponseEntity<MockIngestResponse> ingestMock(@RequestBody(required = false) MockIngestRequest req) {
        return ResponseEntity.ok(service.ingest(req));
    }
}