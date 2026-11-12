package com.proben.api.service.missions.rules;

import java.util.Optional;
import java.util.UUID;

/**
 * Resultado da avaliação de uma condição de regra de missão.
 *
 * @param eventoCondicaoId identificador da condição avaliada
 * @param matched indica se o valor observado satisfez a condição
 * @param reason explicação do resultado da avaliação
 * @param parametroValorId identificador do valor de parâmetro utilizado
 * @param observedValue representação textual do valor observado
 * @param expectedValue representação textual do valor esperado
 * @param avaliacaoParametroValorId identificador da avaliação de qualificação associada
 */

public record ConditionEvaluationResult(
        UUID eventoCondicaoId,
        boolean matched,
        String reason,
        Optional<UUID> parametroValorId,
        String observedValue,
        String expectedValue,
        Optional<UUID> avaliacaoParametroValorId
) {
    public ConditionEvaluationResult {
        parametroValorId = parametroValorId == null ? Optional.empty() : parametroValorId;
        avaliacaoParametroValorId = avaliacaoParametroValorId == null ? Optional.empty() : avaliacaoParametroValorId;
    }

    public ConditionEvaluationResult(UUID eventoCondicaoId, boolean matched, String reason,
            Optional<UUID> parametroValorId, String observedValue, String expectedValue) {
        this(eventoCondicaoId, matched, reason, parametroValorId, observedValue, expectedValue, Optional.empty());
    }
}
