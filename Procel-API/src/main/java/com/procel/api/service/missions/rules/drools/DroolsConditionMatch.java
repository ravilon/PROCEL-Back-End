package com.procel.api.service.missions.rules.drools;

import java.util.UUID;

public record DroolsConditionMatch(
        UUID conditionId,
        DroolsMeasurementFact fact
) {}
