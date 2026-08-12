package com.procel.api.dto.seed;

public record SensorSeedDTO(
        String externalId,
        String nome,
        String tipoNome,
        String compartimentoId
) {}