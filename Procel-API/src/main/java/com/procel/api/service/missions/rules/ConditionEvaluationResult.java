package com.procel.api.service.missions.rules;

import java.util.Optional;
import java.util.UUID;

public record ConditionEvaluationResult(
        UUID eventoCondicaoId,
        boolean matched,
        String reason,
        Optional<UUID> parametroValorId,
        String observedValue,
        String expectedValue
) {
    public ConditionEvaluationResult {
        parametroValorId = parametroValorId == null ? Optional.empty() : parametroValorId;
    }
}
