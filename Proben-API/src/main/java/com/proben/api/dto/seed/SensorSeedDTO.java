package com.proben.api.dto.seed;

/**
 * Objeto de transferencia de dados usado em sensor seed.
 */

public record SensorSeedDTO(
        String externalId,
        String nome,
        String tipoNome,
        String compartimentoId
) {}