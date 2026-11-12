package com.proben.api.service.missions.rules;

import com.proben.api.entity.missions.EventoDefinicao;
import com.proben.api.service.academic.AcademicContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Dados de entrada usados pelo motor ao avaliar um evento de missão.
 *
 * @param evaluationTime instante de referência para a avaliação
 * @param eventDefinition definição do evento e das condições a verificar
 * @param academicContext contexto acadêmico associado, quando existente
 * @param measurements medições disponíveis para a avaliação
 * @param ruleEvaluations resultados de avaliação de regras ambientais disponíveis
 */

public record MissionEvaluationContext(
        Instant evaluationTime,
        EventoDefinicao eventDefinition,
        Optional<AcademicContext> academicContext,
        List<MeasurementFact> measurements,
        List<RuleEvaluationFact> ruleEvaluations
) {
    public MissionEvaluationContext {
        if (evaluationTime == null) throw new IllegalArgumentException("evaluationTime is required");
        if (eventDefinition == null) throw new IllegalArgumentException("eventDefinition is required");
        academicContext = academicContext == null ? Optional.empty() : academicContext;
        measurements = measurements == null ? List.of() : List.copyOf(measurements);
        ruleEvaluations = ruleEvaluations == null ? List.of() : List.copyOf(ruleEvaluations);
    }

    public MissionEvaluationContext(Instant evaluationTime, EventoDefinicao eventDefinition,
            Optional<AcademicContext> academicContext, List<MeasurementFact> measurements) {
        this(evaluationTime, eventDefinition, academicContext, measurements, List.of());
    }
}
