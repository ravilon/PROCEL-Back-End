package com.procel.api.service.missions.rules.drools;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DroolsEvaluationCollector {
    private final List<DroolsConditionMatch> matches = new ArrayList<>();

    public void match(UUID conditionId, DroolsMeasurementFact fact) {
        matches.add(new DroolsConditionMatch(conditionId, fact));
    }

    public List<DroolsConditionMatch> matches() {
        return List.copyOf(matches);
    }
}
