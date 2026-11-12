package com.proben.api.service.missions.rules;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Componente de mission rule evaluation result usado pelo modulo API.
 */

public record MissionRuleEvaluationResult(
        UUID eventDefinitionId,
        boolean matched,
        Instant evaluatedAt,
        List<ConditionEvaluationResult> conditionResults,
        List<MeasurementFact> evidences,
        String reason
) {
    public MissionRuleEvaluationResult {
        conditionResults = conditionResults == null ? List.of() : List.copyOf(conditionResults);
        evidences = evidences == null ? List.of() : List.copyOf(evidences);
    }
}
