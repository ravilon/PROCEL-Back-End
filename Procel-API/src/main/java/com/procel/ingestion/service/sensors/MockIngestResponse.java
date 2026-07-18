package com.procel.ingestion.service.sensors;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumo da ingestao mockada executada.")
public record MockIngestResponse(
        @Schema(description = "Sensor usado para gerar medicoes.", example = "SII-001")
        String sensorExternalId,
        @Schema(description = "Tipo do sensor.", example = "SII_SMART")
        String tipoNome,
        @Schema(description = "Quantidade de parametros definidos para o tipo de sensor.", example = "5")
        int defsCount,
        @Schema(description = "Quantidade de medicoes criadas.", example = "60")
        int medicoesGeradas,
        @Schema(description = "Quantidade de valores parametrizados criados.", example = "300")
        int valoresGerados
) {}
