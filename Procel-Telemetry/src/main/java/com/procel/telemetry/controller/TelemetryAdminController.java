package com.procel.telemetry.controller;

import com.procel.telemetry.dto.TelemetryEventDTOs;
import com.procel.telemetry.entity.RawTelemetryStatus;
import com.procel.telemetry.entity.TelemetrySource;
import com.procel.telemetry.service.TelemetryQueryService;
import com.procel.telemetry.service.TelemetryReprocessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/telemetry/events")
public class TelemetryAdminController {
    private final TelemetryQueryService service;
    private final TelemetryReprocessService reprocessService;

    public TelemetryAdminController(TelemetryQueryService service, TelemetryReprocessService reprocessService) {
        this.service = service;
        this.reprocessService = reprocessService;
    }

    @GetMapping
    public TelemetryEventDTOs.EventPageResponse list(
            @RequestParam(required = false) TelemetrySource source,
            @RequestParam(required = false) RawTelemetryStatus status,
            @RequestParam(required = false) String sensorId,
            @RequestParam(required = false) String producerId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(source, status, sensorId, producerId, messageId, from, to, page, size);
    }

    @GetMapping("/{id}")
    public TelemetryEventDTOs.EventResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/reprocess")
    public TelemetryEventDTOs.ReprocessResponse reprocess(
            @PathVariable String id,
            @RequestBody TelemetryEventDTOs.ReprocessRequest request,
            Authentication authentication
    ) {
        return reprocessService.reprocess(id, request, authentication.getName());
    }
}
