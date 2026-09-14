package com.procel.api.service.missions.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.config.MissionRuleEngineProperties;
import com.procel.api.entity.missions.EventoJanelaAvaliacao;
import com.procel.api.entity.missions.EventoJanelaAvaliacaoStatus;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoOcorrenciaEvidenciaPapel;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.service.academic.AcademicContext;
import com.procel.api.service.missions.EventoJanelaAvaliacaoService;
import com.procel.api.service.missions.EventoOcorrenciaService;
import com.procel.api.service.missions.rules.MeasurementFact;
import com.procel.api.service.missions.rules.MissionEvaluationContext;
import com.procel.api.service.missions.rules.MissionRuleEvaluationResult;
import com.procel.api.service.missions.rules.drools.DroolsMissionRuleEngine;
import com.procel.api.service.missions.rules.drools.DroolsMissionRuleException;
import com.procel.api.service.missions.rules.drools.DroolsRuleEngineSettings;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class MissionTemporalWindowEvaluationService {
    private static final String EVALUATOR_VERSION = "DROOLS_TEMPORAL_V1";

    private final EventoJanelaAvaliacaoService janelaService;
    private final EventoOcorrenciaService ocorrenciaService;
    private final EventoDefinicaoRepository eventoDefinicaoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MissionEvaluationProperties evaluationProperties;
    private final MissionRuleEngineProperties ruleEngineProperties;
    private final MissionTemporalActivityProcessor temporalActivityProcessor;
    private final ApiObservabilityMetrics metrics;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<Duration, DroolsMissionRuleEngine> droolsEngines = new ConcurrentHashMap<>();

    public MissionTemporalWindowEvaluationService(
            EventoJanelaAvaliacaoService janelaService,
            EventoOcorrenciaService ocorrenciaService,
            EventoDefinicaoRepository eventoDefinicaoRepository,
            JdbcTemplate jdbcTemplate,
            MissionEvaluationProperties evaluationProperties,
            MissionRuleEngineProperties ruleEngineProperties,
            MissionTemporalActivityProcessor temporalActivityProcessor,
            ApiObservabilityMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.janelaService = janelaService;
        this.ocorrenciaService = ocorrenciaService;
        this.eventoDefinicaoRepository = eventoDefinicaoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.evaluationProperties = evaluationProperties;
        this.ruleEngineProperties = ruleEngineProperties;
        this.temporalActivityProcessor = temporalActivityProcessor;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void prewarmTemporalRules() {
        var settings = evaluationProperties.getTemporalWindows();
        if (!settings.isEnabled() || !settings.isDroolsEnabled()) {
            return;
        }
        eventoDefinicaoRepository.findActiveMeasurementEvents(
                com.procel.api.entity.missions.EventoTipoDisparo.MEDICAO_RECEBIDA,
                com.procel.api.entity.missions.EventoModoAvaliacao.DURACAO
        ).forEach(event -> droolsEngine().evaluate(new MissionEvaluationContext(
                Instant.EPOCH,
                event,
                Optional.empty(),
                List.of()
        )));
    }

    @Transactional
    public WindowEvaluationOutcome evaluateClaimed(UUID janelaId, Instant now) {
        var settings = evaluationProperties.getTemporalWindows();
        if (!settings.isEnabled()) {
            return WindowEvaluationOutcome.skipped("Temporal windows are disabled");
        }
        if (!settings.isDroolsEnabled()) {
            janelaService.marcarFailed(janelaId, "Temporal Drools evaluation is disabled");
            return WindowEvaluationOutcome.failed("Temporal Drools evaluation is disabled");
        }

        EventoJanelaAvaliacao janela = janelaService.buscar(janelaId);
        if (janela.getStatus() == EventoJanelaAvaliacaoStatus.SATISFEITA) {
            return WindowEvaluationOutcome.satisfied();
        }
        if (janela.getStatus() != EventoJanelaAvaliacaoStatus.PROCESSING) {
            return WindowEvaluationOutcome.skipped("Window is not claimed");
        }
        if (janela.getFimPrevistoEm().isAfter(now)) {
            janelaService.marcarRetry(janelaId, janela.getFimPrevistoEm(), "Window is not ready yet");
            return WindowEvaluationOutcome.retry("Window is not ready yet");
        }

        try {
            JsonNode snapshot = objectMapper.readTree(janela.getContextoSnapshot());
            String sensorExternalId = requiredText(snapshot, "sensorExternalId");
            List<MeasurementFact> facts = factsForWindow(janela, sensorExternalId);
            MissionRuleEvaluationResult result = droolsEngine().evaluate(new MissionEvaluationContext(
                    janela.getFimPrevistoEm(),
                    janela.getEventoDefinicao(),
                    Optional.empty(),
                    facts
            ));
            if (!result.matched()) {
                janelaService.invalidar(janelaId, result.reason());
                return WindowEvaluationOutcome.invalidated(result.reason());
            }

            EventoOcorrencia occurrence = persistOccurrence(janela, sensorExternalId, result, facts.size());
            attachOccurrenceEvidence(occurrence.getId(), result);
            janelaService.satisfazer(janelaId);
            if (settings.isActivitiesEnabled()) {
                try {
                    temporalActivityProcessor.processSatisfiedOccurrence(occurrence.getId(), result.evaluatedAt());
                } catch (com.procel.api.service.missions.MissionEventActivityProcessingException ex) {
                    if (ex.permanent()) {
                        temporalActivityProcessor.invalidateOccurrence(occurrence.getId(), rootMessage(ex));
                        return WindowEvaluationOutcome.failed(rootMessage(ex));
                    }
                    throw ex;
                }
            }
            return WindowEvaluationOutcome.satisfied();
        } catch (DroolsMissionRuleException | IllegalArgumentException ex) {
            janelaService.marcarFailed(janelaId, rootMessage(ex));
            return WindowEvaluationOutcome.failed(rootMessage(ex));
        } catch (DataAccessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(rootMessage(ex), ex);
        }
    }

    private DroolsMissionRuleEngine droolsEngine() {
        Duration maximumSampleGap = evaluationProperties.getTemporalWindows().getMaximumSampleGap();
        return droolsEngines.computeIfAbsent(maximumSampleGap, gap -> new DroolsMissionRuleEngine(
                temporalDroolsSettings(gap),
                metrics
        ));
    }

    private DroolsRuleEngineSettings temporalDroolsSettings(Duration maximumSampleGap) {
        DroolsRuleEngineSettings base = DroolsRuleEngineSettings.from(ruleEngineProperties.drools());
        return new DroolsRuleEngineSettings(
                base.maxFactsPerEvaluation(),
                base.maxCacheEntries(),
                base.cacheExpiration(),
                base.compilationTimeout(),
                base.evaluationTimeout(),
                maximumSampleGap,
                base.maximumEvaluationSpan()
        );
    }

    private EventoOcorrencia persistOccurrence(
            EventoJanelaAvaliacao janela,
            String sensorExternalId,
            MissionRuleEvaluationResult result,
            int factCount
    ) {
        String key = "event:" + janela.getEventoDefinicao().getId() + ":window:" + janela.getId();
        EventoOcorrencia occurrence = ocorrenciaService.buscarPorChaveIdempotencia(key)
                .orElseGet(() -> ocorrenciaService.registrarOcorrencia(
                        new EventoOcorrenciaService.RegistrarOcorrenciaCommand(
                                janela.getEventoDefinicao().getId(),
                                janela.getCompartimento().getId(),
                                janela.getPeriodoAula() == null ? null : janela.getPeriodoAula().getId(),
                                sensorExternalId,
                                janela.getInicioEm(),
                                janela.getFimPrevistoEm(),
                                result.evaluatedAt(),
                                key,
                                occurrenceSnapshot(janela, result, factCount)
                        )
                ));
        if (occurrence.getStatus() == EventoOcorrenciaStatus.DETECTADO) {
            occurrence = ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.CONFIRMADO);
        }
        return occurrence;
    }

    private void attachOccurrenceEvidence(UUID occurrenceId, MissionRuleEvaluationResult result) {
        result.evidences().stream()
                .filter(fact -> fact.medicaoId() != null)
                .filter(fact -> fact.parametroValorId() != null)
                .forEach(fact -> ocorrenciaService.anexarEvidencia(
                        new EventoOcorrenciaService.AnexarEvidenciaCommand(
                                occurrenceId,
                                fact.medicaoId(),
                                fact.parametroValorId(),
                                EventoOcorrenciaEvidenciaPapel.CONDICAO
                        )
                ));
    }

    private List<MeasurementFact> factsForWindow(EventoJanelaAvaliacao janela, String sensorExternalId) {
        return jdbcTemplate.query("""
                select m.id as medicao_id,
                       pv.id as parametro_valor_id,
                       pd.id as parametro_def_id,
                       pd.nome as parametro_nome,
                       pd.data_type,
                       pv.numeric_value,
                       pv.boolean_value,
                       pv.text_value,
                       m.timestamp as measured_at,
                       s.external_id as sensor_external_id,
                       c.id as compartimento_id
                from medicao m
                join sensor s on s.external_id = m.sensor_external_id
                join compartimento c on c.id = s.compartimento_id
                join parametro_valor pv on pv.medicao_id = m.id
                join parametro_def pd on pd.id = pv.parametro_def_id
                where c.id = ?
                  and s.external_id = ?
                  and m.timestamp >= ?
                  and m.timestamp <= ?
                order by m.timestamp asc, m.id asc, pd.nome asc, pv.id asc
                """, this::fact,
                janela.getCompartimento().getId(),
                sensorExternalId,
                Timestamp.from(janela.getInicioEm()),
                Timestamp.from(janela.getFimPrevistoEm()));
    }

    private MeasurementFact fact(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal numeric = rs.getBigDecimal("numeric_value");
        Boolean bool = rs.getObject("boolean_value", Boolean.class);
        return new MeasurementFact(
                rs.getObject("medicao_id", UUID.class),
                rs.getObject("parametro_valor_id", UUID.class),
                rs.getObject("parametro_def_id", UUID.class),
                rs.getString("parametro_nome"),
                DataType.valueOf(rs.getString("data_type")),
                numeric,
                bool,
                rs.getString("text_value"),
                rs.getTimestamp("measured_at").toInstant(),
                rs.getString("sensor_external_id"),
                rs.getString("compartimento_id")
        );
    }

    private String occurrenceSnapshot(
            EventoJanelaAvaliacao janela,
            MissionRuleEvaluationResult result,
            int factCount
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventDefinitionId", janela.getEventoDefinicao().getId().toString());
        root.put("janelaAvaliacaoId", janela.getId().toString());
        root.put("compartimentoId", janela.getCompartimento().getId());
        if (janela.getPeriodoAula() == null) root.putNull("periodoAulaId"); else root.put("periodoAulaId", janela.getPeriodoAula().getId().toString());
        root.put("inicioEm", janela.getInicioEm().toString());
        root.put("fimPrevistoEm", janela.getFimPrevistoEm().toString());
        root.put("evaluatedAt", result.evaluatedAt().toString());
        root.put("evaluatorVersion", EVALUATOR_VERSION);
        root.put("factCount", factCount);
        root.put("reason", result.reason());
        ArrayNode evidence = root.putArray("evidences");
        result.evidences().forEach(fact -> {
            ObjectNode node = evidence.addObject();
            if (fact.medicaoId() == null) node.putNull("medicaoId"); else node.put("medicaoId", fact.medicaoId().toString());
            if (fact.parametroValorId() == null) node.putNull("parametroValorId"); else node.put("parametroValorId", fact.parametroValorId().toString());
            node.put("measuredAt", fact.measuredAt().toString());
        });
        return root.toString();
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required in temporal window snapshot");
        }
        return value.asText();
    }

    private static String rootMessage(Throwable throwable) {
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

    public record WindowEvaluationOutcome(String status, String reason) {
        static WindowEvaluationOutcome satisfied() { return new WindowEvaluationOutcome("satisfied", "Window satisfied"); }
        static WindowEvaluationOutcome invalidated(String reason) { return new WindowEvaluationOutcome("invalidated", reason); }
        static WindowEvaluationOutcome failed(String reason) { return new WindowEvaluationOutcome("failed", reason); }
        static WindowEvaluationOutcome retry(String reason) { return new WindowEvaluationOutcome("retry", reason); }
        static WindowEvaluationOutcome skipped(String reason) { return new WindowEvaluationOutcome("skipped", reason); }
    }
}
