package com.procel.ingestion.dto.sensors;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class MedicaoDTOs {

    public record MedicaoResponse(
            UUID id,
            String sensorExternalId,
            String tipoNome,
            String compartimentoId,
            Instant timestamp,
            Instant receivedAt,
            String source,
            Map<String, Object> valores
    ) {}

}