package com.procel.ingestion.dto.sensors;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class MedicaoDTOs {

    @Schema(description = "Medicao registrada por um sensor, com valores parametrizados por nome.")
    public record MedicaoResponse(
            @Schema(description = "Identificador da medicao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6")
            UUID id,
            @Schema(description = "Identificador externo do sensor.", example = "SII-001")
            String sensorExternalId,
            @Schema(description = "Tipo do sensor.", example = "SII_SMART")
            String tipoNome,
            @Schema(description = "Compartimento associado ao sensor.", example = "2")
            String compartimentoId,
            @Schema(description = "Timestamp medido pelo sensor.", example = "2026-04-18T20:00:00Z")
            Instant timestamp,
            @Schema(description = "Instante de recebimento pela API.", example = "2026-04-18T20:00:01Z")
            Instant receivedAt,
            @Schema(description = "Origem da medicao.", example = "mock")
            String source,
            @Schema(description = "Valores medidos, indexados pelo nome do parametro.", example = "{\"temperature_c\":24.5,\"presence\":true}")
            Map<String, Object> valores
    ) {}

}
