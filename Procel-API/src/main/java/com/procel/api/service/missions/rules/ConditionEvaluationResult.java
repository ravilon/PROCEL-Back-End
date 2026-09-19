package com.procel.api.service.missions.rules;

import java.util.Optional;
import java.util.UUID;

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