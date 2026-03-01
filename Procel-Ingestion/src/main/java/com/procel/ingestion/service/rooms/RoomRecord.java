package com.procel.ingestion.service.rooms;

import java.math.BigDecimal;

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