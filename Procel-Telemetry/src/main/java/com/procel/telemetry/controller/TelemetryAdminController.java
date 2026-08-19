package com.procel.telemetry.controller;

import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.entity.TelemetrySource;
import com.procel.telemetry.service.TelemetryQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/telemetry/events")
public class TelemetryAdminController {
    private final TelemetryQueryService service;

    public TelemetryAdminController(TelemetryQueryService service) {
        this.service = service;
    }

    @GetMapping
    public TelemetryEventDTOs.EventPageResponse list(
            @RequestParam(required = false) TelemetrySource source,
            @RequestParam(required = false) RawTelemetryStatus status,
            @RequestParam(required = false) String sensorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(source, status, sensorId, from, to, page, size);
    }

    @GetMapping("/{id}")
    public TelemetryEventDTOs.EventResponse get(@PathVariable String id) {
        return service.get(id);
    }
}
