package com.proben.api.service.rooms;

import java.math.BigDecimal;

/**
 * Componente de room record usado pelo modulo API.
 */

public record RoomRecord(
        Long externalId,
        String campusNome,
        String predioNome,
        String unidadeNome,
        String compartimentoNome,
        String tipo,
        Integer pavimento,
        Integer capacidade,
        BigDecimal area,
        String lotacaoRaw
) {}