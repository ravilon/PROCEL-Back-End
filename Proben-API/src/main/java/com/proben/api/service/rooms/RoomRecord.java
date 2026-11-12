package com.proben.api.service.rooms;

import java.math.BigDecimal;

/**
 * Dados normalizados de uma sala retornados pelo catálogo acadêmico de origem.
 *
 * @param externalId identificador da sala na origem
 * @param campusNome nome do campus
 * @param predioNome nome do prédio
 * @param unidadeNome nome da unidade acadêmica
 * @param compartimentoNome nome da sala ou compartimento
 * @param tipo tipo de compartimento informado pela origem
 * @param pavimento pavimento da sala, quando informado
 * @param capacidade capacidade da sala, quando informada
 * @param area área da sala, quando informada
 * @param lotacaoRaw valor original de lotação recebido da origem
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
