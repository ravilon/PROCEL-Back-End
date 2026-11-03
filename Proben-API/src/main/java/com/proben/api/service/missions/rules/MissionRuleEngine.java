package com.proben.api.service.missions.rules;

public interface MissionRuleEngine {
    MissionRuleEvaluationResult evaluate(MissionEvaluationContext context);
}
