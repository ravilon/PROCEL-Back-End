package com.procel.api.service.missions.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoOcorrenciaEvidenciaPapel;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.ParametroValor;
import com.procel.api.exception.ConflictException;
import com.procel.api.exception.NotFoundException;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.repository.sensors.MedicaoRepository;
import com.procel.api.repository.sensors.ParametroValorRepository;
import com.procel.api.service.academic.AcademicContext;
import com.procel.api.service.academic.AcademicContextResolver;
import com.procel.api.service.missions.EventoAvaliacaoRequestService;
import com.procel.api.service.missions.EventoAvaliacaoRequestService.EventoAvaliacaoWork;
import com.procel.api.service.missions.MissionEventActivityProcessingException;
import com.procel.api.service.missions.MissionEventActivityProcessor;
import com.procel.api.service.missions.EventoOcorrenciaService;
import com.procel.api.service.missions.rules.ConditionEvaluationResult;
import com.procel.api.service.missions.rules.MeasurementFactFactory;
import com.procel.api.service.missions.rules.MissionEvaluationContext;
import com.procel.api.service.missions.rules.MissionRuleEngine;
import com.procel.api.service.missions.rules.MissionRuleEvaluationResult;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MissionEventEvaluationProcessor {
    private static final String EVALUATOR_VERSION = "SIMPLE_V1";

    private final MedicaoRepository medicaoRepository;
    private final ParametroValorRepository parametroValorRepository;
    private final EventoDefinicaoRepository eventoDefinicaoRepository;
    private final AcademicContextResolver academicContextResolver;
    private final MeasurementFactFactory measurementFactFactory;
    private final MissionRuleEngine missionRuleEngine;
    private final MissionTemporalWindowUpdateService temporalWindowUpdateService;
    private final EventoOcorrenciaService ocorrenciaService;
    private final MissionEventActivityProcessor activityProcessor;
    private final EventoAvaliacaoRequestService requestService;
    private final ApiObservabilityMetrics metrics;
    private final ObjectMapper objectMapper;

    public MissionEventEvaluationProcessor(
            MedicaoRepository medicaoRepository,
            ParametroValorRepository parametroValorRepository,
            EventoDefinicaoRepository eventoDefinicaoRepository,
            AcademicContextResolver academicContextResolver,
            MeasurementFactFactory measurementFactFactory,
            MissionRuleEngine missionRuleEngine,
            MissionTemporalWindowUpdateService temporalWindowUpdateService,
            EventoOcorrenciaService ocorrenciaService,
            MissionEventActivityProcessor activityProcessor,
            EventoAvaliacaoRequestService requestService,
            ApiObservabilityMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.medicaoRepository = medicaoRepository;
        this.parametroValorRepository = parametroValorRepository;
        this.eventoDefinicaoRepository = eventoDefinicaoRepository;
        this.academicContextResolver = academicContextResolver;
        this.measurementFactFactory = measurementFactFactory;
        this.missionRuleEngine = missionRuleEngine;
        this.temporalWindowUpdateService = temporalWindowUpdateService;
        this.ocorrenciaService = ocorrenciaService;
        this.activityProcessor = activityProcessor;
        this.requestService = requestService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProcessingOutcome process(EventoAvaliacaoWork work, Instant evaluationTime) {
        try {
            Medicao medicao = medicaoRepository.findById(work.medicaoId())
                    .orElseThrow(() -> new NotFoundException("Medicao not found id=" + work.medicaoId()));
            if (medicao.getSensor() == null || medicao.getSensor().getCompartimento() == null) {
                throw new MissionEventEvaluationFailure("Measurement sensor or compartment is missing", true);
            }

            String compartimentoId = medicao.getSensor().getCompartimento().getId();
            AcademicContext academicContext = resolveAcademicContext(compartimentoId, medicao.getTimestamp());
            Optional<AcademicContext> optionalAcademic = academicContext.empty()
                    ? Optional.empty()
                    : Optional.of(academicContext);

            List<EventoDefinicao> events = eventoDefinicaoRepository.findActiveInstantMeasurementEvents(
                    EventoTipoDisparo.MEDICAO_RECEBIDA,
                    EventoModoAvaliacao.INSTANTANEO
            );
            List<ParametroValor> valores = parametroValorRepository.findAllByMedicao_Id(medicao.getId());
            var facts = measurementFactFactory.from(medicao, valores);
            int detected = 0;

            if (!events.isEmpty()) {
                for (EventoDefinicao event : events) {
                    MissionRuleEvaluationResult result = evaluate(event, optionalAcademic, facts, evaluationTime);
                    if (!result.matched()) {
                        continue;
                    }
                    EventoOcorrencia occurrence = persistOccurrenceAndEvidence(medicao, academicContext, event, result, evaluationTime);
                    activityProcessor.process(
                            occurrence.getId(),
                            optionalAcademic,
                            Optional.empty(),
                            evaluationTime
                    );
                    detected++;
                }
            }

            int temporalWindowsTouched = temporalWindowUpdateService.processMeasurement(
                    medicao,
                    academicContext,
                    facts,
                    evaluationTime
            );
            if (events.isEmpty() && temporalWindowsTouched == 0) {
                return ProcessingOutcome.ignored("No applicable event definitions");
            }

            requestService.markCompleted(work.requestId());
            return ProcessingOutcome.completed(detected);
        } catch (MissionEventEvaluationFailure ex) {
            throw ex;
        } catch (MissionEventActivityProcessingException ex) {
            if (ex.permanent()) {
                throw MissionEventEvaluationFailure.permanent(rootMessage(ex), ex);
            }
            throw MissionEventEvaluationFailure.transientFailure(rootMessage(ex), ex);
        } catch (ConflictException | IllegalArgumentException | NotFoundException ex) {
            throw MissionEventEvaluationFailure.permanent(rootMessage(ex), ex);
        } catch (DataAccessException ex) {
            throw MissionEventEvaluationFailure.transientFailure(rootMessage(ex), ex);
        } catch (RuntimeException ex) {
            throw MissionEventEvaluationFailure.transientFailure(rootMessage(ex), ex);
        }
    }

    private AcademicContext resolveAcademicContext(String compartimentoId, Instant timestamp) {
        try {
            return academicContextResolver.resolve(compartimentoId, timestamp);
        } catch (ConflictException ex) {
            throw MissionEventEvaluationFailure.permanent(rootMessage(ex), ex);
        }
    }

    private MissionRuleEvaluationResult evaluate(
            EventoDefinicao event,
            Optional<AcademicContext> academicContext,
            List<com.procel.api.service.missions.rules.MeasurementFact> facts,
            Instant evaluationTime
    ) {
        try {
            return missionRuleEngine.evaluate(new MissionEvaluationContext(
                    evaluationTime,
                    event,
                    academicContext,
                    facts
            ));
        } catch (IllegalArgumentException ex) {
            throw MissionEventEvaluationFailure.permanent(rootMessage(ex), ex);
        }
    }

    private EventoOcorrencia persistOccurrenceAndEvidence(
            Medicao medicao,
            AcademicContext academicContext,
            EventoDefinicao event,
            MissionRuleEvaluationResult result,
            Instant evaluationTime
    ) {
        String key = "event:" + event.getId() + ":measurement:" + medicao.getId();
        EventoOcorrencia occurrence = ocorrenciaService.buscarPorChaveIdempotencia(key)
                .orElseGet(() -> ocorrenciaService.registrarOcorrencia(
                        new EventoOcorrenciaService.RegistrarOcorrenciaCommand(
                                event.getId(),
                                medicao.getSensor().getCompartimento().getId(),
                                academicContext.empty() ? null : academicContext.periodoAulaId(),
                                medicao.getSensor().getExternalId(),
                                medicao.getTimestamp(),
                                medicao.getTimestamp(),
                                evaluationTime,
                                key,
                                snapshot(medicao, academicContext, event, result, evaluationTime)
                        )
                ));
        if (occurrence.getStatus() != EventoOcorrenciaStatus.PROCESSADO) {
            occurrence = ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.CONFIRMADO);
        }
        metrics.missionEventDetected();
        UUID occurrenceId = occurrence.getId();

        result.conditionResults().stream()
                .filter(ConditionEvaluationResult::matched)
                .map(ConditionEvaluationResult::parametroValorId)
                .flatMap(Optional::stream)
                .distinct()
                .forEach(parametroValorId -> ocorrenciaService.anexarEvidencia(
                        new EventoOcorrenciaService.AnexarEvidenciaCommand(
                                occurrenceId,
                                medicao.getId(),
                                parametroValorId,
                                EventoOcorrenciaEvidenciaPapel.CONDICAO
                        )
                ));
        return occurrence;
    }

    private String snapshot(
            Medicao medicao,
            AcademicContext academicContext,
            EventoDefinicao event,
            MissionRuleEvaluationResult result,
            Instant evaluationTime
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventDefinitionId", event.getId().toString());
        root.put("nome", event.getNome());
        root.put("tipoDisparo", event.getTipoDisparo().name());
        root.put("modoAvaliacao", event.getModoAvaliacao().name());
        root.put("operadorLogico", event.getOperadorLogico().name());
        root.put("politicaAtribuicao", event.getPoliticaAtribuicao().name());
        root.set("conditions", conditions(event));
        root.set("conditionResults", conditionResults(result));
        root.put("medicaoId", medicao.getId().toString());
        root.put("sensorExternalId", medicao.getSensor().getExternalId());
        root.put("compartimentoId", medicao.getSensor().getCompartimento().getId());
        root.put("evaluationTime", evaluationTime.toString());
        root.put("evaluatorVersion", EVALUATOR_VERSION);
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
        return root.toString();
    }

    private ArrayNode conditions(EventoDefinicao event) {
        ArrayNode array = objectMapper.createArrayNode();
        event.getCondicoes().stream()
                .sorted(Comparator.comparing(c -> c.getOrdem(), Comparator.nullsLast(Integer::compareTo)))
                .forEach(condition -> {
                    ObjectNode node = array.addObject();
                    node.put("id", condition.getId().toString());
                    node.put("parametroDefId", condition.getParametroDef().getId().toString());
                    node.put("operador", condition.getOperador().name());
                    node.put("obrigatoria", condition.isObrigatoria());
                    node.put("ordem", condition.getOrdem());
                    node.put("ativo", condition.isAtivo());
                });
        return array;
    }

    private ArrayNode conditionResults(MissionRuleEvaluationResult result) {
        ArrayNode array = objectMapper.createArrayNode();
        result.conditionResults().forEach(conditionResult -> {
            ObjectNode node = array.addObject();
            node.put("eventoCondicaoId", conditionResult.eventoCondicaoId().toString());
            node.put("matched", conditionResult.matched());
            node.put("reason", conditionResult.reason());
            conditionResult.parametroValorId().ifPresentOrElse(
                    id -> node.put("parametroValorId", id.toString()),
                    () -> node.putNull("parametroValorId")
            );
            if (conditionResult.observedValue() == null) node.putNull("observedValue"); else node.put("observedValue", conditionResult.observedValue());
            if (conditionResult.expectedValue() == null) node.putNull("expectedValue"); else node.put("expectedValue", conditionResult.expectedValue());
        });
        return array;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public record ProcessingOutcome(
            boolean ignored,
            int detectedEvents,
            String reason
    ) {
        static ProcessingOutcome ignored(String reason) {
            return new ProcessingOutcome(true, 0, reason);
        }

        static ProcessingOutcome completed(int detectedEvents) {
            return new ProcessingOutcome(false, detectedEvents, "Evaluation completed");
        }
    }
}
