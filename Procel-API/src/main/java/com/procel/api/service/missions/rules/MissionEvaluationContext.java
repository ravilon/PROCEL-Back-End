package com.procel.api.service.missions.rules;

import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.service.academic.AcademicContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record MissionEvaluationContext(
        Instant evaluationTime,
        EventoDefinicao eventDefinition,
        Optional<AcademicContext> academicContext,
        List<MeasurementFact> measurements
) {
    public MissionEvaluationContext {
        if (evaluationTime == null) throw new IllegalArgumentException("evaluationTime is required");
        if (eventDefinition == null) throw new IllegalArgumentException("eventDefinition is required");
        academicContext = academicContext == null ? Optional.empty() : academicContext;
        measurements = measurements == null ? List.of() : List.copyOf(measurements);
    }
}
