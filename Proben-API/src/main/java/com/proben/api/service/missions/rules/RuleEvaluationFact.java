package com.proben.api.service.missions.rules;

import com.proben.api.entity.sensors.AvaliacaoResultado;

import java.util.UUID;

public record RuleEvaluationFact(
        UUID avaliacaoId,
        UUID regraId,
        UUID parametroValorId,
        AvaliacaoResultado resultado
) {}
