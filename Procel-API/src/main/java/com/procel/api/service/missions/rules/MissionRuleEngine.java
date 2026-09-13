package com.procel.api.service.missions.rules;

public interface MissionRuleEngine {
    MissionRuleEvaluationResult evaluate(MissionEvaluationContext context);
}
