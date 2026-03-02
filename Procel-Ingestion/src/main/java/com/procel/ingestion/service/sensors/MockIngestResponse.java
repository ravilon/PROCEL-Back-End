package com.procel.ingestion.service.sensors;

public record MockIngestResponse(
        String sensorExternalId,
        String tipoNome,
        int defsCount,
        int medicoesGeradas,
        int valoresGerados
) {}