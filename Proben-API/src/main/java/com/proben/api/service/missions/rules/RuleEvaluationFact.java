package com.proben.api.service.missions.rules;

import com.proben.api.entity.sensors.AvaliacaoResultado;

import java.util.UUID;

/**
 * Componente de rule evaluation fact usado pelo modulo API.
 */

public record RuleEvaluationFact(
        UUID avaliacaoId,
        UUID regraId,
        UUID parametroValorId,
        AvaliacaoResultado resultado
) {}
