package com.proben.api.service.missions.rules;

/**
 * Componente de mission rule engine usado pelo modulo API.
 */

public interface MissionRuleEngine {
    MissionRuleEvaluationResult evaluate(MissionEvaluationContext context);
}
