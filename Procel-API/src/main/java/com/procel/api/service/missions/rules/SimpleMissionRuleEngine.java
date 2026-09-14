package com.procel.api.service.missions.rules;

import com.procel.api.entity.missions.EventoCondicao;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.RegraOperador;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class SimpleMissionRuleEngine implements MissionRuleEngine {

    @Override
    public MissionRuleEvaluationResult evaluate(MissionEvaluationContext context) {
        validateSupported(context);

        EventoDefinicao event = context.eventDefinition();
        if (!event.isAtivo()) {
            return new MissionRuleEvaluationResult(
                    event.getId(),
                    false,
                    context.evaluationTime(),
                    List.of(),
                    List.of(),
                    "Event definition is inactive"
            );
        }

        Map<UUID, MeasurementFact> factsByParameter = factsByParameter(context.measurements());
        List<EventoCondicao> activeConditions = activeConditions(event);
        List<ConditionEvaluationResult> conditionResults = new ArrayList<>();
        List<MeasurementFact> evidences = new ArrayList<>();

        for (EventoCondicao condition : activeConditions) {
            ConditionEvaluation evaluation = evaluateCondition(condition, factsByParameter);
            conditionResults.add(evaluation.result());
            evaluation.evidence().ifPresent(evidences::add);
        }

        List<ConditionEvaluationResult> requiredResults = activeConditions.stream()
                .filter(Objects::nonNull)
                .filter(eventoCondicao -> eventoCondicao.isObrigatoria())
                .map(condition -> conditionResults.get(activeConditions.indexOf(condition)))
                .toList();
        if (requiredResults.isEmpty()) {
            return result(event, context, false, conditionResults, evidences, "No active required conditions");
        }

        boolean matched = switch (event.getOperadorLogico()) {
            case ALL -> requiredResults.stream().allMatch(result -> result != null && result.matched());
            case ANY -> requiredResults.stream().anyMatch(result -> result != null && result.matched());
        };
        String reason = matched
                ? "Required conditions matched"
                : "Required conditions did not match";
        return result(event, context, matched, conditionResults, evidences, reason);
    }

    private static void validateSupported(MissionEvaluationContext context) {
        EventoDefinicao event = context.eventDefinition();
        if (event.getTipoDisparo() != EventoTipoDisparo.MEDICAO_RECEBIDA) {
            throw new IllegalArgumentException("Unsupported event trigger type: " + event.getTipoDisparo());
        }
        if (event.getModoAvaliacao() != EventoModoAvaliacao.INSTANTANEO) {
            throw new IllegalArgumentException("Unsupported evaluation mode: " + event.getModoAvaliacao());
        }
        if (event.getOperadorLogico() == null) {
            throw new IllegalArgumentException("operadorLogico is required");
        }
    }

    private static Map<UUID, MeasurementFact> factsByParameter(List<MeasurementFact> measurements) {
        Map<UUID, MeasurementFact> facts = new LinkedHashMap<>();
        for (MeasurementFact fact : measurements) {
            MeasurementFact previous = facts.putIfAbsent(fact.parametroDefId(), fact);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Ambiguous measurement facts for parametroDefId=" + fact.parametroDefId()
                );
            }
        }
        return facts;
    }

    private static List<EventoCondicao> activeConditions(EventoDefinicao event) {
        return eventConditions(event).stream()
                .filter(Objects::nonNull)
                .filter(condicao -> Boolean.TRUE.equals(condicao.isAtivo()))
                .sorted(Comparator.comparing(
                        (EventoCondicao condicao) -> condicao.getOrdem(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }

    private static List<EventoCondicao> eventConditions(EventoDefinicao event) {
        return event.getCondicoes();
    }

    private static ConditionEvaluation evaluateCondition(
            EventoCondicao condition,
            Map<UUID, MeasurementFact> factsByParameter
    ) {
        UUID parametroDefId = condition.getParametroDef().getId();
        MeasurementFact fact = factsByParameter.get(parametroDefId);
        if (fact == null) {
            return noEvidence(condition, "Missing parameter fact for parametroDefId=" + parametroDefId);
        }

        if (fact.dataType() != condition.getParametroDef().getDataType()) {
            return withEvidence(condition, fact, false, "Parameter data type does not match condition", expectedValue(condition));
        }

        return switch (fact.dataType()) {
            case NUMERIC -> evaluateNumeric(condition, fact);
            case BOOLEAN -> evaluateBoolean(condition, fact);
            case TEXT -> evaluateText(condition, fact);
        };
    }

    private static ConditionEvaluation evaluateNumeric(EventoCondicao condition, MeasurementFact fact) {
        BigDecimal value = fact.numericValue();
        if (value == null) {
            return withEvidence(condition, fact, false, "Observed numeric value is null", expectedValue(condition));
        }
        BigDecimal v1 = condition.getValorNumeric1();
        BigDecimal v2 = condition.getValorNumeric2();
        boolean matched = switch (condition.getOperador()) {
            case GT -> v1 != null && value.compareTo(v1) > 0;
            case GTE -> v1 != null && value.compareTo(v1) >= 0;
            case LT -> v1 != null && value.compareTo(v1) < 0;
            case LTE -> v1 != null && value.compareTo(v1) <= 0;
            case EQ -> v1 != null && value.compareTo(v1) == 0;
            case NEQ -> v1 != null && value.compareTo(v1) != 0;
            case BETWEEN -> v1 != null && v2 != null && value.compareTo(v1) >= 0 && value.compareTo(v2) <= 0;
            case OUTSIDE -> v1 != null && v2 != null && (value.compareTo(v1) < 0 || value.compareTo(v2) > 0);
            case CONTAINS -> false;
        };
        return withEvidence(condition, fact, matched, matched ? "Condition matched" : "Condition did not match", expectedValue(condition));
    }

    private static ConditionEvaluation evaluateBoolean(EventoCondicao condition, MeasurementFact fact) {
        Boolean value = fact.booleanValue();
        if (value == null) {
            return withEvidence(condition, fact, false, "Observed boolean value is null", expectedValue(condition));
        }
        Boolean expected = condition.getValorBoolean();
        boolean matched = switch (condition.getOperador()) {
            case EQ -> expected != null && Objects.equals(value, expected);
            case NEQ -> expected != null && !Objects.equals(value, expected);
            default -> false;
        };
        return withEvidence(condition, fact, matched, matched ? "Condition matched" : "Condition did not match", expectedValue(condition));
    }

    private static ConditionEvaluation evaluateText(EventoCondicao condition, MeasurementFact fact) {
        String value = fact.textValue();
        if (value == null) {
            return withEvidence(condition, fact, false, "Observed text value is null", expectedValue(condition));
        }
        String expected = condition.getValorText();
        boolean matched = switch (condition.getOperador()) {
            case EQ -> expected != null && value.equals(expected);
            case NEQ -> expected != null && !value.equals(expected);
            case CONTAINS -> expected != null && value.contains(expected);
            default -> false;
        };
        return withEvidence(condition, fact, matched, matched ? "Condition matched" : "Condition did not match", expectedValue(condition));
    }

    private static ConditionEvaluation noEvidence(EventoCondicao condition, String reason) {
        return new ConditionEvaluation(new ConditionEvaluationResult(
                condition.getId(),
                false,
                reason,
                Optional.empty(),
                null,
                expectedValue(condition)
        ), Optional.empty());
    }

    private static ConditionEvaluation withEvidence(
            EventoCondicao condition,
            MeasurementFact fact,
            boolean matched,
            String reason,
            String expectedValue
    ) {
        return new ConditionEvaluation(new ConditionEvaluationResult(
                condition.getId(),
                matched,
                reason,
                Optional.ofNullable(fact.parametroValorId()),
                fact.observedValue(),
                expectedValue
        ), Optional.of(fact));
    }

    private static String expectedValue(EventoCondicao condition) {
        RegraOperador operator = condition.getOperador();
        DataType type = condition.getParametroDef().getDataType();
        return switch (type) {
            case NUMERIC -> switch (operator) {
                case BETWEEN, OUTSIDE -> numeric(condition.getValorNumeric1()) + ".." + numeric(condition.getValorNumeric2());
                default -> numeric(condition.getValorNumeric1());
            };
            case BOOLEAN -> condition.getValorBoolean() == null ? null : condition.getValorBoolean().toString();
            case TEXT -> condition.getValorText();
        };
    }

    private static String numeric(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static MissionRuleEvaluationResult result(
            EventoDefinicao event,
            MissionEvaluationContext context,
            boolean matched,
            List<ConditionEvaluationResult> conditionResults,
            List<MeasurementFact> evidences,
            String reason
    ) {
        return new MissionRuleEvaluationResult(
                event.getId(),
                matched,
                context.evaluationTime(),
                conditionResults,
                evidences,
                reason
        );
    }

    private record ConditionEvaluation(
            ConditionEvaluationResult result,
            Optional<MeasurementFact> evidence
    ) {}
}
