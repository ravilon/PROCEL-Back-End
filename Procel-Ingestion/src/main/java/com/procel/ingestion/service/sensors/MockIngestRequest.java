package com.procel.ingestion.service.sensors;

public record MockIngestRequest(
        String sensorExternalId, // opcional; se null pega o primeiro sensor
        Integer minutesBack,     // default 60
        Integer everySeconds,    // default 10
        String source            // default "mock"
) {}