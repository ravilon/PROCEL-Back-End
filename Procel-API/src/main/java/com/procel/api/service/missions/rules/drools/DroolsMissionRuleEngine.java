package com.procel.api.service.missions.rules.drools;

import com.procel.api.entity.missions.EventoCondicao;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOperadorLogico;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.RegraOperador;
import com.procel.api.service.missions.rules.ConditionEvaluationResult;
import com.procel.api.service.missions.rules.MeasurementFact;
import com.procel.api.service.missions.rules.MissionEvaluationContext;
import com.procel.api.service.missions.rules.MissionRuleEngine;
import com.procel.api.service.missions.rules.MissionRuleEvaluationResult;
import org.drools.core.time.SessionPseudoClock;
import org.kie.api.KieBase;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.utils.KieHelper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DroolsMissionRuleEngine implements MissionRuleEngine {
    private static final Comparator<DroolsMeasurementFact> FACT_ORDER = Comparator
            .comparing(DroolsMeasurementFact::measuredAt, Comparator.nullsLast(Instant::compareTo))
            .thenComparing(f -> f.parametroDefId().toString())
            .thenComparing(f -> f.parametroValorId() == null ? "" : f.parametroValorId().toString());

    private final int maxFacts;
    private final Duration maxSampleGap;
    private final ConcurrentMap<String, KieBase> compiledRules = new ConcurrentHashMap<>();

    public DroolsMissionRuleEngine(int maxFacts, long maxSampleGapSeconds) {
        this.maxFacts = maxFacts <= 0 ? 1_000 : maxFacts;
        this.maxSampleGap = Duration.ofSeconds(maxSampleGapSeconds <= 0 ? 600 : maxSampleGapSeconds);
    }

    @Override
    public MissionRuleEvaluationResult evaluate(MissionEvaluationContext context) {
        Objects.requireNonNull(context, "context is required");
        EventoDefinicao event = context.eventDefinition();
        validateSupported(event);
        if (!event.isAtivo()) {
            return new MissionRuleEvaluationResult(event.getId(), false, context.evaluationTime(), List.of(), List.of(),
                    "Event definition is inactive");
        }

        List<EventoCondicao> activeConditions = activeConditions(event);
        if (activeConditions.stream().noneMatch(EventoCondicao::isObrigatoria)) {
            return new MissionRuleEvaluationResult(event.getId(), false, context.evaluationTime(), List.of(), List.of(),
                    "No active required conditions");
        }

        List<DroolsMeasurementFact> facts = normalizeFacts(context.measurements(), event.getModoAvaliacao());
        KieBase kbase = compiledRules.computeIfAbsent(fingerprint(event, activeConditions), ignored -> compile(event, activeConditions));
        DroolsEvaluationCollector collector = evaluateWithDrools(kbase, facts, context.evaluationTime());

        if (event.getModoAvaliacao() == EventoModoAvaliacao.INSTANTANEO) {
            return evaluateInstant(context, activeConditions, facts, collector.matches());
        }
        return evaluateDuration(context, activeConditions, facts, collector.matches());
    }

    private static void validateSupported(EventoDefinicao event) {
        if (event.getTipoDisparo() != EventoTipoDisparo.MEDICAO_RECEBIDA) {
            throw new DroolsMissionRuleException("Unsupported event trigger type for Drools proof of concept: " + event.getTipoDisparo());
        }
        if (event.getModoAvaliacao() != EventoModoAvaliacao.INSTANTANEO
                && event.getModoAvaliacao() != EventoModoAvaliacao.DURACAO) {
            throw new DroolsMissionRuleException("Unsupported evaluation mode for Drools proof of concept: " + event.getModoAvaliacao());
        }
        if (event.getOperadorLogico() == null) {
            throw new DroolsMissionRuleException("operadorLogico is required");
        }
        if (event.getModoAvaliacao() == EventoModoAvaliacao.DURACAO) {
            if (event.getOperadorLogico() != EventoOperadorLogico.ALL) {
                throw new DroolsMissionRuleException("DURACAO proof of concept supports only ALL conditions");
            }
            if (event.getJanelaSegundos() == null || event.getJanelaSegundos() <= 0) {
                throw new DroolsMissionRuleException("janelaSegundos must be positive for DURACAO");
            }
            if (event.getDuracaoMinimaSegundos() == null || event.getDuracaoMinimaSegundos() <= 0) {
                throw new DroolsMissionRuleException("duracaoMinimaSegundos must be positive for DURACAO");
            }
        }
    }

    private List<DroolsMeasurementFact> normalizeFacts(List<MeasurementFact> measurements, EventoModoAvaliacao mode) {
        if (measurements.size() > maxFacts) {
            throw new DroolsMissionRuleException("Drools fact limit exceeded: " + measurements.size() + " > " + maxFacts);
        }

        List<DroolsMeasurementFact> facts = measurements.stream()
                .map(this::copyFact)
                .sorted(FACT_ORDER)
                .toList();
        Set<String> sensors = facts.stream()
                .map(DroolsMeasurementFact::sensorExternalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> compartments = facts.stream()
                .map(DroolsMeasurementFact::compartimentoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sensors.size() > 1 || compartments.size() > 1) {
            throw new DroolsMissionRuleException("Ambiguous facts: evaluation must not mix sensors or compartments");
        }
        if (mode == EventoModoAvaliacao.INSTANTANEO) {
            assertNoDuplicateParameters(facts);
        } else if (facts.stream().anyMatch(f -> f.measuredAt() == null)) {
            throw new DroolsMissionRuleException("measuredAt is required for DURACAO evaluation");
        }
        return facts;
    }

    private DroolsMeasurementFact copyFact(MeasurementFact fact) {
        return new DroolsMeasurementFact(
                fact.medicaoId(),
                fact.parametroValorId(),
                fact.parametroDefId(),
                fact.parametroNome(),
                fact.dataType(),
                fact.numericValue(),
                fact.booleanValue(),
                fact.textValue(),
                fact.measuredAt(),
                fact.sensorExternalId(),
                fact.compartimentoId()
        );
    }

    private static void assertNoDuplicateParameters(List<DroolsMeasurementFact> facts) {
        Set<UUID> seen = new LinkedHashSet<>();
        for (DroolsMeasurementFact fact : facts) {
            if (!seen.add(fact.parametroDefId())) {
                throw new DroolsMissionRuleException("Ambiguous measurement facts for parametroDefId=" + fact.parametroDefId());
            }
        }
    }

    private DroolsEvaluationCollector evaluateWithDrools(
            KieBase kbase,
            List<DroolsMeasurementFact> facts,
            Instant evaluationTime
    ) {
        KieSessionConfiguration configuration = org.kie.api.KieServices.Factory.get().newKieSessionConfiguration();
        configuration.setOption(ClockTypeOption.get("pseudo"));
        KieSession session = kbase.newKieSession(configuration, null);
        try {
            DroolsEvaluationCollector collector = new DroolsEvaluationCollector();
            session.setGlobal("collector", collector);
            SessionPseudoClock clock = session.getSessionClock();
            for (DroolsMeasurementFact fact : facts) {
                advanceClock(clock, fact.measuredAt());
                session.insert(fact);
                session.fireAllRules();
            }
            advanceClock(clock, evaluationTime);
            session.fireAllRules();
            return collector;
        } finally {
            session.dispose();
        }
    }

    private static void advanceClock(SessionPseudoClock clock, Instant target) {
        if (target == null) return;
        long currentMillis = clock.getCurrentTime();
        long targetMillis = target.toEpochMilli();
        if (targetMillis > currentMillis) {
            clock.advanceTime(targetMillis - currentMillis, TimeUnit.MILLISECONDS);
        }
    }

    private MissionRuleEvaluationResult evaluateInstant(
            MissionEvaluationContext context,
            List<EventoCondicao> activeConditions,
            List<DroolsMeasurementFact> facts,
            List<DroolsConditionMatch> matches
    ) {
        Map<UUID, DroolsMeasurementFact> factByParameter = facts.stream()
                .collect(Collectors.toMap(DroolsMeasurementFact::parametroDefId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, DroolsMeasurementFact> matchedFactsByCondition = matches.stream()
                .collect(Collectors.toMap(DroolsConditionMatch::conditionId, DroolsConditionMatch::fact, (a, b) -> a, LinkedHashMap::new));

        List<ConditionEvaluationResult> conditionResults = new ArrayList<>();
        List<MeasurementFact> evidences = new ArrayList<>();
        for (EventoCondicao condition : activeConditions) {
            DroolsMeasurementFact observed = factByParameter.get(condition.getParametroDef().getId());
            DroolsMeasurementFact matched = matchedFactsByCondition.get(condition.getId());
            conditionResults.add(conditionResult(condition, observed, matched != null));
            if (observed != null) {
                evidences.add(toMeasurementFact(observed));
            }
        }

        boolean matched = aggregate(context.eventDefinition(), activeConditions, conditionResults);
        return new MissionRuleEvaluationResult(
                context.eventDefinition().getId(),
                matched,
                context.evaluationTime(),
                conditionResults,
                evidences,
                matched ? "Required conditions matched" : "Required conditions did not match"
        );
    }

    private MissionRuleEvaluationResult evaluateDuration(
            MissionEvaluationContext context,
            List<EventoCondicao> activeConditions,
            List<DroolsMeasurementFact> facts,
            List<DroolsConditionMatch> matches
    ) {
        EventoDefinicao event = context.eventDefinition();
        Instant windowStart = context.evaluationTime().minusSeconds(event.getJanelaSegundos());
        Instant windowEnd = context.evaluationTime();
        Set<UUID> requiredConditionIds = activeConditions.stream()
                .filter(EventoCondicao::isObrigatoria)
                .map(EventoCondicao::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Instant, Set<UUID>> matchedConditionsByTime = new LinkedHashMap<>();
        for (DroolsConditionMatch match : matches) {
            Instant measuredAt = match.fact().measuredAt();
            if (measuredAt != null && measuredAt.isAfter(windowStart) && !measuredAt.isAfter(windowEnd)) {
                matchedConditionsByTime
                        .computeIfAbsent(measuredAt, ignored -> new LinkedHashSet<>())
                        .add(match.conditionId());
            }
        }

        List<Instant> sampleTimes = facts.stream()
                .map(DroolsMeasurementFact::measuredAt)
                .filter(t -> t.isAfter(windowStart) && !t.isAfter(windowEnd))
                .distinct()
                .sorted()
                .toList();

        Instant segmentStart = null;
        Instant previousSatisfied = null;
        Instant completedAt = null;
        for (Instant sampleTime : sampleTimes) {
            boolean satisfied = matchedConditionsByTime.getOrDefault(sampleTime, Set.of()).containsAll(requiredConditionIds);
            boolean gapOk = previousSatisfied == null || !Duration.between(previousSatisfied, sampleTime).minus(maxSampleGap).isPositive();
            if (!satisfied || !gapOk) {
                segmentStart = satisfied ? sampleTime : null;
            } else if (segmentStart == null) {
                segmentStart = sampleTime;
            }
            if (satisfied) {
                previousSatisfied = sampleTime;
                if (!Duration.between(segmentStart, sampleTime).minusSeconds(event.getDuracaoMinimaSegundos()).isNegative()) {
                    completedAt = sampleTime;
                    break;
                }
            } else {
                previousSatisfied = null;
            }
        }

        boolean matched = completedAt != null;
        Instant evidenceEnd = completedAt;
        Map<UUID, DroolsMeasurementFact> evidenceByCondition = matches.stream()
                .filter(match -> evidenceEnd == null || !match.fact().measuredAt().isAfter(evidenceEnd))
                .collect(Collectors.toMap(DroolsConditionMatch::conditionId, DroolsConditionMatch::fact, (a, b) -> b, LinkedHashMap::new));

        List<ConditionEvaluationResult> conditionResults = new ArrayList<>();
        for (EventoCondicao condition : activeConditions) {
            DroolsMeasurementFact evidence = evidenceByCondition.get(condition.getId());
            conditionResults.add(conditionResult(condition, evidence, matched && condition.isObrigatoria()));
        }

        List<MeasurementFact> evidences = matches.stream()
                .map(DroolsConditionMatch::fact)
                .filter(f -> evidenceEnd == null || !f.measuredAt().isAfter(evidenceEnd))
                .distinct()
                .map(this::toMeasurementFact)
                .toList();
        String reason = matched
                ? "Duration matched using semi-open window (" + windowStart + ", " + windowEnd + "] and max sample gap " + maxSampleGap
                : "Duration did not remain satisfied for " + event.getDuracaoMinimaSegundos() + " seconds";
        return new MissionRuleEvaluationResult(event.getId(), matched, context.evaluationTime(), conditionResults, evidences, reason);
    }

    private static boolean aggregate(
            EventoDefinicao event,
            List<EventoCondicao> activeConditions,
            List<ConditionEvaluationResult> conditionResults
    ) {
        List<ConditionEvaluationResult> required = new ArrayList<>();
        for (int i = 0; i < activeConditions.size(); i++) {
            if (activeConditions.get(i).isObrigatoria()) {
                required.add(conditionResults.get(i));
            }
        }
        return switch (event.getOperadorLogico()) {
            case ALL -> required.stream().allMatch(ConditionEvaluationResult::matched);
            case ANY -> required.stream().anyMatch(ConditionEvaluationResult::matched);
        };
    }

    private static ConditionEvaluationResult conditionResult(
            EventoCondicao condition,
            DroolsMeasurementFact observed,
            boolean matched
    ) {
        if (observed == null) {
            return new ConditionEvaluationResult(
                    condition.getId(),
                    false,
                    "Missing parameter fact for parametroDefId=" + condition.getParametroDef().getId(),
                    Optional.empty(),
                    null,
                    expectedValue(condition)
            );
        }
        String reason = matched ? "Condition matched" : mismatchReason(condition, observed);
        return new ConditionEvaluationResult(
                condition.getId(),
                matched,
                reason,
                Optional.ofNullable(observed.parametroValorId()),
                observed.observedValue(),
                expectedValue(condition)
        );
    }

    private static String mismatchReason(EventoCondicao condition, DroolsMeasurementFact observed) {
        if (observed.dataType() != condition.getParametroDef().getDataType()) {
            return "Parameter data type does not match condition";
        }
        return switch (observed.dataType()) {
            case NUMERIC -> observed.numericValue() == null ? "Observed numeric value is null" : "Condition did not match";
            case BOOLEAN -> observed.booleanValue() == null ? "Observed boolean value is null" : "Condition did not match";
            case TEXT -> observed.textValue() == null ? "Observed text value is null" : "Condition did not match";
        };
    }

    private static List<EventoCondicao> activeConditions(EventoDefinicao event) {
        return event.getCondicoes().stream()
                .filter(EventoCondicao::isAtivo)
                .sorted(Comparator.comparing(EventoCondicao::getOrdem, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private KieBase compile(EventoDefinicao event, List<EventoCondicao> conditions) {
        String drl = new DrlGenerator(event, conditions).generate();
        try {
            return new KieHelper()
                    .addContent(drl, ResourceType.DRL)
                    .build(EventProcessingOption.STREAM);
        } catch (RuntimeException ex) {
            throw new DroolsMissionRuleException("Drools rule compilation failed for eventDefinitionId=" + event.getId(), ex);
        }
    }

    private static String fingerprint(EventoDefinicao event, List<EventoCondicao> conditions) {
        StringBuilder input = new StringBuilder()
                .append(event.getId()).append('|')
                .append(event.getTipoDisparo()).append('|')
                .append(event.getModoAvaliacao()).append('|')
                .append(event.getOperadorLogico()).append('|')
                .append(event.getJanelaSegundos()).append('|')
                .append(event.getDuracaoMinimaSegundos());
        for (EventoCondicao condition : conditions) {
            input.append('|')
                    .append(condition.getId()).append(':')
                    .append(condition.getParametroDef().getId()).append(':')
                    .append(condition.getParametroDef().getDataType()).append(':')
                    .append(condition.getOperador()).append(':')
                    .append(condition.getValorNumeric1()).append(':')
                    .append(condition.getValorNumeric2()).append(':')
                    .append(condition.getValorBoolean()).append(':')
                    .append(condition.getValorText()).append(':')
                    .append(condition.isObrigatoria()).append(':')
                    .append(condition.getOrdem());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private MeasurementFact toMeasurementFact(DroolsMeasurementFact fact) {
        return new MeasurementFact(
                fact.medicaoId(),
                fact.parametroValorId(),
                fact.parametroDefId(),
                fact.parametroNome(),
                fact.dataType(),
                fact.numericValue(),
                fact.booleanValue(),
                fact.textValue(),
                fact.measuredAt(),
                fact.sensorExternalId(),
                fact.compartimentoId()
        );
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

    private static final class DrlGenerator {
        private final EventoDefinicao event;
        private final List<EventoCondicao> conditions;

        private DrlGenerator(EventoDefinicao event, List<EventoCondicao> conditions) {
            this.event = event;
            this.conditions = conditions;
        }

        private String generate() {
            StringBuilder drl = new StringBuilder()
                    .append("package com.procel.api.generated.missions;\n")
                    .append("import ").append(DroolsMeasurementFact.class.getCanonicalName()).append(";\n")
                    .append("import ").append(DroolsEvaluationCollector.class.getCanonicalName()).append(";\n")
                    .append("import ").append(DataType.class.getCanonicalName()).append(";\n")
                    .append("global DroolsEvaluationCollector collector;\n");
            for (EventoCondicao condition : conditions) {
                drl.append(rule(condition));
            }
            return drl.toString();
        }

        private String rule(EventoCondicao condition) {
            validateCondition(condition);
            String expression = expression(condition);
            return """

                    rule "%s"
                    when
                        $fact : DroolsMeasurementFact(parametroDefId == java.util.UUID.fromString("%s"), dataType == DataType.%s, %s)
                    then
                        collector.match(java.util.UUID.fromString("%s"), $fact);
                    end
                    """.formatted(
                    "event_" + event.getId() + "_condition_" + condition.getId(),
                    condition.getParametroDef().getId(),
                    condition.getParametroDef().getDataType(),
                    expression,
                    condition.getId()
            );
        }

        private static void validateCondition(EventoCondicao condition) {
            if (condition.getParametroDef() == null || condition.getParametroDef().getId() == null) {
                throw new DroolsMissionRuleException("Condition parameter definition is required");
            }
            if (condition.getOperador() == null) {
                throw new DroolsMissionRuleException("Condition operator is required");
            }
        }

        private static String expression(EventoCondicao condition) {
            return switch (condition.getParametroDef().getDataType()) {
                case NUMERIC -> numericExpression(condition);
                case BOOLEAN -> booleanExpression(condition);
                case TEXT -> textExpression(condition);
            };
        }

        private static String numericExpression(EventoCondicao condition) {
            return switch (condition.getOperador()) {
                case GT -> numericValue("numericValue.compareTo(%s) > 0", condition.getValorNumeric1());
                case GTE -> numericValue("numericValue.compareTo(%s) >= 0", condition.getValorNumeric1());
                case LT -> numericValue("numericValue.compareTo(%s) < 0", condition.getValorNumeric1());
                case LTE -> numericValue("numericValue.compareTo(%s) <= 0", condition.getValorNumeric1());
                case EQ -> numericValue("numericValue.compareTo(%s) == 0", condition.getValorNumeric1());
                case NEQ -> numericValue("numericValue.compareTo(%s) != 0", condition.getValorNumeric1());
                case BETWEEN -> numericValue(
                        "numericValue.compareTo(%s) >= 0 && numericValue.compareTo(%s) <= 0",
                        condition.getValorNumeric1(),
                        condition.getValorNumeric2()
                );
                case OUTSIDE -> numericValue(
                        "(numericValue.compareTo(%s) < 0 || numericValue.compareTo(%s) > 0)",
                        condition.getValorNumeric1(),
                        condition.getValorNumeric2()
                );
                case CONTAINS -> throw new DroolsMissionRuleException("CONTAINS is not supported for NUMERIC conditions");
            };
        }

        private static String numericValue(String template, BigDecimal... values) {
            for (BigDecimal value : values) {
                if (value == null) return "numericValue != null && false";
            }
            Object[] arguments = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                arguments[i] = "new java.math.BigDecimal(\"" + values[i].toPlainString() + "\")";
            }
            return "numericValue != null && " + template.formatted(arguments);
        }

        private static String booleanExpression(EventoCondicao condition) {
            if (condition.getValorBoolean() == null) return "booleanValue != null && false";
            return switch (condition.getOperador()) {
                case EQ -> "booleanValue != null && booleanValue == " + condition.getValorBoolean();
                case NEQ -> "booleanValue != null && booleanValue != " + condition.getValorBoolean();
                default -> "booleanValue != null && false";
            };
        }

        private static String textExpression(EventoCondicao condition) {
            String expected = condition.getValorText();
            if (expected == null) return "textValue != null && false";
            String literal = "\"" + escapeJava(expected) + "\"";
            return switch (condition.getOperador()) {
                case EQ -> "textValue != null && textValue.equals(" + literal + ")";
                case NEQ -> "textValue != null && !textValue.equals(" + literal + ")";
                case CONTAINS -> "textValue != null && textValue.contains(" + literal + ")";
                default -> "textValue != null && false";
            };
        }

        private static String escapeJava(String value) {
            StringBuilder escaped = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\' -> escaped.append("\\\\");
                    case '"' -> escaped.append("\\\"");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> escaped.append(c);
                }
            }
            return escaped.toString();
        }
    }
}
