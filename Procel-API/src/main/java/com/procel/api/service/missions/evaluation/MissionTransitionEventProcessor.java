package com.procel.api.service.missions.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.entity.missions.EventoCondicao;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoOcorrenciaEvidenciaPapel;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.RegraOperador;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.repository.sensors.ParametroValorRepository;
import com.procel.api.service.academic.AcademicContext;
import com.procel.api.service.missions.EventoOcorrenciaService;
import com.procel.api.service.missions.MissionEventActivityProcessor;
import com.procel.api.service.missions.rules.ConditionEvaluationResult;
import com.procel.api.service.missions.rules.MeasurementFact;
import com.procel.api.service.missions.rules.MeasurementFactFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MissionTransitionEventProcessor {
    private static final String EVALUATOR_VERSION = "TRANSITION_V1";

    private final MissionEvaluationProperties properties;
    private final EventoDefinicaoRepository eventoDefinicaoRepository;
    private final ParametroValorRepository parametroValorRepository;
    private final MeasurementFactFactory measurementFactFactory;
    private final EventoOcorrenciaService ocorrenciaService;
    private final MissionEventActivityProcessor activityProcessor;
    private final ApiObservabilityMetrics metrics;
    private final ObjectMapper objectMapper;

    public MissionTransitionEventProcessor(
            MissionEvaluationProperties properties,
            EventoDefinicaoRepository eventoDefinicaoRepository,
            ParametroValorRepository parametroValorRepository,
            MeasurementFactFactory measurementFactFactory,
            EventoOcorrenciaService ocorrenciaService,
            MissionEventActivityProcessor activityProcessor,
            ApiObservabilityMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.eventoDefinicaoRepository = eventoDefinicaoRepository;
        this.parametroValorRepository = parametroValorRepository;
        this.measurementFactFactory = measurementFactFactory;
        this.ocorrenciaService = ocorrenciaService;
        this.activityProcessor = activityProcessor;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    public TransitionProcessingOutcome processMeasurement(
            Medicao medicao,
            AcademicContext academicContext,
            List<MeasurementFact> currentFacts,
            Instant evaluationTime
    ) {
        if (!properties.getTemporalWindows().isEnabled()) {
            return TransitionProcessingOutcome.none();
        }

        List<EventoDefinicao> events = eventoDefinicaoRepository.findActiveMeasurementEvents(
                EventoTipoDisparo.MEDICAO_RECEBIDA,
                EventoModoAvaliacao.TRANSICAO
        );
        if (events.isEmpty()) {
            return TransitionProcessingOutcome.none();
        }

        Map<UUID, MeasurementFact> factsByParameter = factsByParameter(currentFacts);
        int detected = 0;
        for (EventoDefinicao event : events) {
            TransitionEvaluationResult result = evaluate(event, medicao, factsByParameter);
            if (!result.matched()) {
                continue;
            }
            EventoOcorrencia occurrence = persistOccurrenceAndEvidence(
                    medicao,
                    academicContext,
                    event,
                    result,
                    evaluationTime
            );
            if (properties.getTemporalWindows().isActivitiesEnabled()) {
                activityProcessor.process(
                        occurrence.getId(),
                        academicContext.empty() ? Optional.empty() : Optional.of(academicContext),
                        Optional.empty(),
                        evaluationTime
                );
            }
            detected++;
        }

        return new TransitionProcessingOutcome(events.size(), detected);
    }

    private TransitionEvaluationResult evaluate(
            EventoDefinicao event,
            Medicao medicao,
            Map<UUID, MeasurementFact> factsByParameter
    ) {
        validate(event);
        Comparator<EventoCondicao> conditionOrder = Comparator.comparing(
                condition -> condition == null ? null : condition.getOrdem(),
                (left, right) -> {
                    if (left == null && right == null) {
                        return 0;
                    }
                    if (left == null) {
                        return 1;
                    }
                    if (right == null) {
                        return -1;
                    }
                    return Integer.compare(left, right);
                }
        );
        List<EventoCondicao> activeConditions = event.getCondicoes().stream()
                .filter(condition -> condition != null && condition.isAtivo())
                .sorted(conditionOrder)
                .toList();
        if (activeConditions.stream().noneMatch(condition -> condition != null && condition.isObrigatoria())) {
            return new TransitionEvaluationResult(false, List.of(), List.of(), "No active required conditions");
        }

        List<TransitionConditionResult> results = new ArrayList<>();
        for (EventoCondicao condition : activeConditions) {
            results.add(evaluateCondition(condition, medicao, factsByParameter));
        }

        List<TransitionConditionResult> required = results.stream()
                .filter(result -> result.condition().isObrigatoria())
                .toList();
        boolean currentMatched = switch (event.getOperadorLogico()) {
            case ALL -> required.stream().allMatch(result -> result.conditionResult().matched());
            case ANY -> required.stream().anyMatch(result -> result.conditionResult().matched());
        };
        boolean changed = required.stream()
                .anyMatch(result -> result.conditionResult().matched() && result.changed());
        boolean matched = currentMatched && changed;
        String reason = matched
                ? "Required transition conditions matched and at least one required value changed"
                : currentMatched
                        ? "Required conditions matched but no required value changed"
                        : "Required transition conditions did not match";
        return new TransitionEvaluationResult(matched, results, changedResults(results), reason);
    }

    private TransitionConditionResult evaluateCondition(
            EventoCondicao condition,
            Medicao medicao,
            Map<UUID, MeasurementFact> factsByParameter
    ) {
        UUID parametroDefId = condition.getParametroDef().getId();
        MeasurementFact current = factsByParameter.get(parametroDefId);
        if (current == null) {
            return noEvidence(condition, "Missing parameter fact for parametroDefId=" + parametroDefId);
        }
        if (current.dataType() != condition.getParametroDef().getDataType()) {
            return withEvidence(condition, current, Optional.empty(), false, false,
                    "Parameter data type does not match condition");
        }

        boolean conditionMatched = conditionMatches(condition, current);
        Optional<MeasurementFact> previous = previousFact(medicao, parametroDefId);
        if (previous.isEmpty()) {
            return withEvidence(condition, current, Optional.empty(), conditionMatched, false,
                    conditionMatched
                            ? "Condition matched but previous value is absent"
                            : "Condition did not match and previous value is absent");
        }
        if (previous.get().dataType() != current.dataType()) {
            return withEvidence(condition, current, previous, conditionMatched, false,
                    "Previous value data type does not match current value");
        }

        boolean changed = valueChanged(previous.get(), current);
        return withEvidence(condition, current, previous, conditionMatched, changed,
                conditionMatched
                        ? changed ? "Condition matched and value changed" : "Condition matched but value did not change"
                        : "Condition did not match");
    }

    private Optional<MeasurementFact> previousFact(Medicao medicao, UUID parametroDefId) {
        return parametroValorRepository.findPreviousForSensorParameter(
                        medicao.getSensor().getExternalId(),
                        parametroDefId,
                        medicao.getTimestamp(),
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .map(measurementFactFactory::from);
    }

    private EventoOcorrencia persistOccurrenceAndEvidence(
            Medicao medicao,
            AcademicContext academicContext,
            EventoDefinicao event,
            TransitionEvaluationResult result,
            Instant evaluationTime
    ) {
        String key = "event:" + event.getId() + ":transition:measurement:" + medicao.getId();
        Optional<EventoOcorrencia> existing = ocorrenciaService.buscarPorChaveIdempotencia(key);
        EventoOcorrencia occurrence = existing.orElseGet(() -> ocorrenciaService.registrarOcorrencia(
                new EventoOcorrenciaService.RegistrarOcorrenciaCommand(
                        event.getId(),
                        medicao.getSensor().getCompartimento().getId(),
                        academicContext.empty() ? null : academicContext.periodoAulaId(),
                        medicao.getSensor().getExternalId(),
                        result.firstPreviousMeasuredAt().orElse(medicao.getTimestamp()),
                        medicao.getTimestamp(),
                        medicao.getTimestamp(),
                        key,
                        snapshot(medicao, academicContext, event, result, evaluationTime)
                )
        ));
        if (occurrence.getStatus() != EventoOcorrenciaStatus.PROCESSADO) {
            occurrence = ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.CONFIRMADO);
        }
        if (existing.isEmpty()) {
            metrics.missionEventDetected();
        }

        UUID occurrenceId = occurrence.getId();
        for (TransitionEvidence evidence : result.evidences()) {
            ocorrenciaService.anexarEvidencia(new EventoOcorrenciaService.AnexarEvidenciaCommand(
                    occurrenceId,
                    evidence.previous().medicaoId(),
                    evidence.previous().parametroValorId(),
                    EventoOcorrenciaEvidenciaPapel.ANTES
            ));
            ocorrenciaService.anexarEvidencia(new EventoOcorrenciaService.AnexarEvidenciaCommand(
                    occurrenceId,
                    evidence.current().medicaoId(),
                    evidence.current().parametroValorId(),
                    EventoOcorrenciaEvidenciaPapel.DEPOIS
            ));
        }
        return occurrence;
    }

    private void validate(EventoDefinicao event) {
        if (event.getTipoDisparo() != EventoTipoDisparo.MEDICAO_RECEBIDA) {
            throw new IllegalArgumentException("Unsupported transition trigger type: " + event.getTipoDisparo());
        }
        if (event.getModoAvaliacao() != EventoModoAvaliacao.TRANSICAO) {
            throw new IllegalArgumentException("Unsupported transition evaluation mode: " + event.getModoAvaliacao());
        }
        if (event.getOperadorLogico() == null) {
            throw new IllegalArgumentException("operadorLogico is required");
        }
    }

    private static Map<UUID, MeasurementFact> factsByParameter(List<MeasurementFact> currentFacts) {
        Map<UUID, MeasurementFact> facts = new LinkedHashMap<>();
        for (MeasurementFact fact : currentFacts) {
            MeasurementFact previous = facts.putIfAbsent(fact.parametroDefId(), fact);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Ambiguous measurement facts for parametroDefId=" + fact.parametroDefId()
                );
            }
        }
        return facts;
    }

    private static List<TransitionEvidence> changedResults(List<TransitionConditionResult> results) {
        return results.stream()
                .filter(result -> result.conditionResult().matched())
                .filter(result -> result.changed())
                .flatMap(result -> result.previous()
                        .map(previous -> new TransitionEvidence(previous, result.current()))
                        .stream())
                .distinct()
                .toList();
    }

    private static TransitionConditionResult noEvidence(EventoCondicao condition, String reason) {
        return new TransitionConditionResult(
                condition,
                null,
                Optional.empty(),
                false,
                conditionResult(condition, false, reason, null),
                null
        );
    }

    private static TransitionConditionResult withEvidence(
            EventoCondicao condition,
            MeasurementFact current,
            Optional<MeasurementFact> previous,
            boolean matched,
            boolean changed,
            String reason
    ) {
        return new TransitionConditionResult(
                condition,
                current,
                previous,
                changed,
                conditionResult(condition, matched, reason, current),
                previous.map(fact -> fact.observedValue()).orElse(null)
        );
    }

    private static ConditionEvaluationResult conditionResult(
            EventoCondicao condition,
            boolean matched,
            String reason,
            MeasurementFact current
    ) {
        return new ConditionEvaluationResult(
                condition.getId(),
                matched,
                reason,
                current == null ? Optional.empty() : Optional.ofNullable(current.parametroValorId()),
                current == null ? null : current.observedValue(),
                expectedValue(condition)
        );
    }

    private static boolean conditionMatches(EventoCondicao condition, MeasurementFact fact) {
        return switch (fact.dataType()) {
            case NUMERIC -> numericMatches(condition, fact.numericValue());
            case BOOLEAN -> booleanMatches(condition, fact.booleanValue());
            case TEXT -> textMatches(condition, fact.textValue());
        };
    }

    private static boolean numericMatches(EventoCondicao condition, BigDecimal value) {
        if (value == null) return false;
        BigDecimal v1 = condition.getValorNumeric1();
        BigDecimal v2 = condition.getValorNumeric2();
        return switch (condition.getOperador()) {
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
    }

    private static boolean booleanMatches(EventoCondicao condition, Boolean value) {
        if (value == null) return false;
        Boolean expected = condition.getValorBoolean();
        return switch (condition.getOperador()) {
            case EQ -> expected != null && Objects.equals(value, expected);
            case NEQ -> expected != null && !Objects.equals(value, expected);
            default -> false;
        };
    }

    private static boolean textMatches(EventoCondicao condition, String value) {
        if (value == null) return false;
        String expected = condition.getValorText();
        return switch (condition.getOperador()) {
            case EQ -> expected != null && value.equals(expected);
            case NEQ -> expected != null && !value.equals(expected);
            case CONTAINS -> expected != null && value.contains(expected);
            default -> false;
        };
    }

    private static boolean valueChanged(MeasurementFact previous, MeasurementFact current) {
        if (previous.dataType() != current.dataType()) return false;
        return switch (current.dataType()) {
            case NUMERIC -> compareNumeric(previous.numericValue(), current.numericValue());
            case BOOLEAN -> !Objects.equals(previous.booleanValue(), current.booleanValue());
            case TEXT -> !Objects.equals(previous.textValue(), current.textValue());
        };
    }

    private static boolean compareNumeric(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null) {
            return !Objects.equals(previous, current);
        }
        return previous.compareTo(current) != 0;
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

    private String snapshot(
            Medicao medicao,
            AcademicContext academicContext,
            EventoDefinicao event,
            TransitionEvaluationResult result,
            Instant evaluationTime
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventDefinitionId", event.getId().toString());
        root.put("nome", event.getNome());
        root.put("tipoDisparo", event.getTipoDisparo().name());
        root.put("modoAvaliacao", event.getModoAvaliacao().name());
        root.put("operadorLogico", event.getOperadorLogico().name());
        root.put("politicaAtribuicao", event.getPoliticaAtribuicao().name());
        root.put("currentMedicaoId", medicao.getId().toString());
        root.put("sensorExternalId", medicao.getSensor().getExternalId());
        root.put("compartimentoId", medicao.getSensor().getCompartimento().getId());
        root.put("evaluationTime", evaluationTime.toString());
        root.put("evaluatorVersion", EVALUATOR_VERSION);
        root.put("reason", result.reason());
        if (academicContext.empty()) {
            root.putNull("periodoAulaId");
            root.putNull("disciplinaId");
            root.putNull("turma");
            root.putNull("periodoLetivo");
            root.putArray("eligiblePersonIds");
        } else {
            root.put("periodoAulaId", academicContext.periodoAulaId().toString());
            if (academicContext.disciplinaId() == null) root.putNull("disciplinaId"); else root.put("disciplinaId", academicContext.disciplinaId());
            if (academicContext.turma() == null) root.putNull("turma"); else root.put("turma", academicContext.turma());
            if (academicContext.periodoLetivo() == null) root.putNull("periodoLetivo"); else root.put("periodoLetivo", academicContext.periodoLetivo());
            ArrayNode people = root.putArray("eligiblePersonIds");
            academicContext.pessoaElegivelIds().forEach(people::add);
        }
        ArrayNode conditionResults = root.putArray("conditionResults");
        result.conditionResults().forEach(conditionResult -> {
            ObjectNode node = conditionResults.addObject();
            node.put("eventoCondicaoId", conditionResult.condition().getId().toString());
            node.put("matched", conditionResult.conditionResult().matched());
            node.put("changed", conditionResult.changed());
            node.put("reason", conditionResult.conditionResult().reason());
            if (conditionResult.previousObservedValue() == null) node.putNull("previousObservedValue"); else node.put("previousObservedValue", conditionResult.previousObservedValue());
            if (conditionResult.conditionResult().observedValue() == null) node.putNull("currentObservedValue"); else node.put("currentObservedValue", conditionResult.conditionResult().observedValue());
            conditionResult.previous().ifPresentOrElse(
                    previous -> node.put("previousParametroValorId", previous.parametroValorId().toString()),
                    () -> node.putNull("previousParametroValorId")
            );
            conditionResult.conditionResult().parametroValorId().ifPresentOrElse(
                    id -> node.put("currentParametroValorId", id.toString()),
                    () -> node.putNull("currentParametroValorId")
            );
        });
        return root.toString();
    }

    public record TransitionProcessingOutcome(int evaluatedEvents, int detectedEvents) {
        static TransitionProcessingOutcome none() {
            return new TransitionProcessingOutcome(0, 0);
        }
    }

    private record TransitionEvaluationResult(
            boolean matched,
            List<TransitionConditionResult> conditionResults,
            List<TransitionEvidence> evidences,
            String reason
    ) {
        Optional<Instant> firstPreviousMeasuredAt() {
            return evidences.stream()
                    .map(evidence -> evidence.previous().measuredAt())
                    .min(Comparator.comparing((Instant instant) -> instant.toEpochMilli()));
        }
    }

    private record TransitionConditionResult(
            EventoCondicao condition,
            MeasurementFact current,
            Optional<MeasurementFact> previous,
            boolean changed,
            ConditionEvaluationResult conditionResult,
            String previousObservedValue
    ) {}

    private record TransitionEvidence(MeasurementFact previous, MeasurementFact current) {}
}
