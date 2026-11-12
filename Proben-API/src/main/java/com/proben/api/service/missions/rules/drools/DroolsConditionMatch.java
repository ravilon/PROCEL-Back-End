package com.proben.api.service.missions.rules.drools;

import java.util.UUID;

/**
 * Componente de drools condition match usado pelo modulo API.
 */

public record DroolsConditionMatch(
        UUID conditionId,
        DroolsMeasurementFact fact
) {}
