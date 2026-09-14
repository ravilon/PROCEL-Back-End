package com.procel.api.service.missions.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.entity.missions.EventoCondicao;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoJanelaEvidenciaPapel;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.RegraOperador;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.service.academic.AcademicContext;
import com.procel.api.service.missions.EventoJanelaAvaliacaoService;
import com.procel.api.service.missions.rules.MeasurementFact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MissionTemporalWindowUpdateService {
    private final EventoDefinicaoRepository eventoDefinicaoRepository;
    private final EventoJanelaAvaliacaoService janelaService;
    private final JdbcTemplate jdbcTemplate;
    private final MissionEvaluationProperties properties;
    private final ObjectMapper objectMapper;

    public MissionTemporalWindowUpdateService(
            EventoDefinicaoRepository eventoDefinicaoRepository,
            EventoJanelaAvaliacaoService janelaService,
            JdbcTemplate jdbcTemplate,
            MissionEvaluationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.eventoDefinicaoRepository = eventoDefinicaoRepository;
        this.janelaService = janelaService;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public int processMeasurement(
            Medicao medicao,
            AcademicContext academicContext,
            List<MeasurementFact> facts,
            Instant evaluationTime
    ) {
        if (!properties.getTemporalWindows().isEnabled()) {
            return 0;
        }
        List<EventoDefinicao> events = eventoDefinicaoRepository.findActiveMeasurementEvents(
                EventoTipoDisparo.MEDICAO_RECEBIDA,
                EventoModoAvaliacao.DURACAO
        );
        int touched = 0;
        for (EventoDefinicao event : events) {
            validateDurationEvent(event);
            touched += processEvent(event, medicao, academicContext, facts, evaluationTime);
        }
        return touched;
    }

    private int processEvent(
            EventoDefinicao event,
            Medicao medicao,
            AcademicContext academicContext,
            List<MeasurementFact> facts,
            Instant evaluationTime
    ) {
        String compartimentoId = medicao.getSensor().getCompartimento().getId();
        UUID periodoAulaId = academicContext.empty() ? null : academicContext.periodoAulaId();
        Instant measuredAt = medicao.getTimestamp();

        int expired = expireGapExceeded(event.getId(), compartimentoId, periodoAulaId, measuredAt);
        TemporalConditionEvaluation current = evaluateCurrent(event, facts);
        List<UUID> activeWindows = activeWindowIds(event.getId(), compartimentoId, periodoAulaId, measuredAt);
        if (!current.matched()) {
            activeWindows.forEach(id -> janelaService.invalidar(id, "Temporal condition interrupted"));
            return expired + Math.max(1, activeWindows.size());
        }

        List<UUID> dueWindows = dueWindowIds(event.getId(), compartimentoId, periodoAulaId, measuredAt);
        if (activeWindows.isEmpty() && !dueWindows.isEmpty()) {
            attachEvidences(dueWindows.getFirst(), medicao.getId(), current, EventoJanelaEvidenciaPapel.ENCERRAMENTO);
            return expired + dueWindows.size();
        }

        if (activeWindows.isEmpty()) {
            Instant end = measuredAt.plusSeconds(event.getDuracaoMinimaSegundos());
            var window = janelaService.abrir(new EventoJanelaAvaliacaoService.AbrirJanelaCommand(
                    event.getId(),
                    compartimentoId,
                    periodoAulaId,
                    measuredAt,
                    end,
                    measuredAt,
                    end,
                    null,
                    snapshot(event, medicao, academicContext, evaluationTime)
            ));
            attachEvidences(window.getId(), medicao.getId(), current, EventoJanelaEvidenciaPapel.INICIO);
            return expired + 1;
        }

        for (UUID windowId : activeWindows) {
            janelaService.atualizarMedicao(windowId, measuredAt, nextEvaluationFor(windowId));
            attachEvidences(windowId, medicao.getId(), current, EventoJanelaEvidenciaPapel.MANUTENCAO);
        }
        return expired + activeWindows.size();
    }

    private Instant nextEvaluationFor(UUID windowId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "select fim_previsto_em from evento_janela_avaliacao where id = ?",
                Timestamp.class,
                windowId
        );
        return Objects.requireNonNull(timestamp, "fim_previsto_em is required").toInstant();
    }

    private List<UUID> activeWindowIds(UUID eventId, String compartimentoId, UUID periodoAulaId, Instant measuredAt) {
        if (periodoAulaId == null) {
            return jdbcTemplate.query("""
                    select id
                    from evento_janela_avaliacao
                    where evento_definicao_id = ?
                      and compartimento_id = ?
                      and periodo_aula_id is null
                      and status = 'ABERTA'
                      and ? >= inicio_em
                      and ? < fim_previsto_em
                    order by inicio_em asc, id asc
                    """, (rs, rowNum) -> rs.getObject("id", UUID.class),
                    eventId,
                    compartimentoId,
                    Timestamp.from(measuredAt),
                    Timestamp.from(measuredAt));
        }
        return jdbcTemplate.query("""
                select id
                from evento_janela_avaliacao
                where evento_definicao_id = ?
                  and compartimento_id = ?
                  and periodo_aula_id = ?
                  and status = 'ABERTA'
                  and ? >= inicio_em
                  and ? < fim_previsto_em
                order by inicio_em asc, id asc
                """, (rs, rowNum) -> rs.getObject("id", UUID.class),
                eventId,
                compartimentoId,
                periodoAulaId,
                Timestamp.from(measuredAt),
                Timestamp.from(measuredAt));
    }

    private List<UUID> dueWindowIds(UUID eventId, String compartimentoId, UUID periodoAulaId, Instant measuredAt) {
        if (periodoAulaId == null) {
            return jdbcTemplate.query("""
                    select id
                    from evento_janela_avaliacao
                    where evento_definicao_id = ?
                      and compartimento_id = ?
                      and periodo_aula_id is null
                      and status = 'ABERTA'
                      and fim_previsto_em <= ?
                    order by fim_previsto_em asc, id asc
                    """, (rs, rowNum) -> rs.getObject("id", UUID.class),
                    eventId,
                    compartimentoId,
                    Timestamp.from(measuredAt));
        }
        return jdbcTemplate.query("""
                select id
                from evento_janela_avaliacao
                where evento_definicao_id = ?
                  and compartimento_id = ?
                  and periodo_aula_id = ?
                  and status = 'ABERTA'
                  and fim_previsto_em <= ?
                order by fim_previsto_em asc, id asc
                """, (rs, rowNum) -> rs.getObject("id", UUID.class),
                eventId,
                compartimentoId,
                periodoAulaId,
                Timestamp.from(measuredAt));
    }

    private int expireGapExceeded(UUID eventId, String compartimentoId, UUID periodoAulaId, Instant measuredAt) {
        if (periodoAulaId == null) {
            return jdbcTemplate.update("""
                    update evento_janela_avaliacao
                    set status = 'EXPIRADA',
                        lease_until = null,
                        last_error = coalesce(last_error, 'Maximum sample gap exceeded'),
                        updated_at = now()
                    where evento_definicao_id = ?
                      and compartimento_id = ?
                      and periodo_aula_id is null
                      and status in ('ABERTA','PROCESSING')
                      and ? < fim_previsto_em
                      and ultima_medicao_em is not null
                      and ultima_medicao_em + (? * interval '1 second') < ?
                    """,
                    eventId,
                    compartimentoId,
                    Timestamp.from(measuredAt),
                    properties.getTemporalWindows().getMaximumSampleGap().toSeconds(),
                    Timestamp.from(measuredAt));
        }
        int updated = jdbcTemplate.update("""
                update evento_janela_avaliacao
                set status = 'EXPIRADA',
                    lease_until = null,
                    last_error = coalesce(last_error, 'Maximum sample gap exceeded'),
                    updated_at = now()
                where evento_definicao_id = ?
                  and compartimento_id = ?
                  and periodo_aula_id = ?
                  and status in ('ABERTA','PROCESSING')
                  and ? < fim_previsto_em
                  and ultima_medicao_em is not null
                  and ultima_medicao_em + (? * interval '1 second') < ?
                """,
                eventId,
                compartimentoId,
                periodoAulaId,
                Timestamp.from(measuredAt),
                properties.getTemporalWindows().getMaximumSampleGap().toSeconds(),
                Timestamp.from(measuredAt));
        return updated;
    }

    private void attachEvidences(
            UUID windowId,
            UUID medicaoId,
            TemporalConditionEvaluation current,
            EventoJanelaEvidenciaPapel papel
    ) {
        current.evidences().stream()
                .map(MeasurementFact::parametroValorId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(parametroValorId -> janelaService.anexarEvidencia(
                        new EventoJanelaAvaliacaoService.AnexarEvidenciaCommand(
                                windowId,
                                medicaoId,
                                parametroValorId,
                                papel
                        )
                ));
    }

    private TemporalConditionEvaluation evaluateCurrent(EventoDefinicao event, List<MeasurementFact> facts) {
        List<EventoCondicao> required = event.getCondicoes().stream()
                .filter(EventoCondicao::isAtivo)
                .filter(EventoCondicao::isObrigatoria)
                .sorted(Comparator.comparing(EventoCondicao::getOrdem, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (required.isEmpty()) {
            throw new IllegalArgumentException("DURACAO event requires active mandatory conditions");
        }
        List<MeasurementFact> evidences = required.stream()
                .map(condition -> factFor(condition, facts))
                .flatMap(Optional::stream)
                .toList();
        boolean matched = required.stream().allMatch(condition ->
                factFor(condition, facts).map(fact -> conditionMatches(condition, fact)).orElse(false)
        );
        return new TemporalConditionEvaluation(matched, evidences);
    }

    private Optional<MeasurementFact> factFor(EventoCondicao condition, List<MeasurementFact> facts) {
        UUID parameterId = condition.getParametroDef().getId();
        return facts.stream()
                .filter(fact -> parameterId.equals(fact.parametroDefId()))
                .findFirst();
    }

    private boolean conditionMatches(EventoCondicao condition, MeasurementFact fact) {
        if (condition.getParametroDef().getDataType() != fact.dataType()) {
            return false;
        }
        return switch (fact.dataType()) {
            case NUMERIC -> numericMatches(condition, fact.numericValue());
            case BOOLEAN -> booleanMatches(condition, fact.booleanValue());
            case TEXT -> textMatches(condition, fact.textValue());
        };
    }

    private static boolean numericMatches(EventoCondicao condition, BigDecimal value) {
        if (value == null) return false;
        return switch (condition.getOperador()) {
            case GT -> compare(value, condition.getValorNumeric1()) > 0;
            case GTE -> compare(value, condition.getValorNumeric1()) >= 0;
            case LT -> compare(value, condition.getValorNumeric1()) < 0;
            case LTE -> compare(value, condition.getValorNumeric1()) <= 0;
            case EQ -> compare(value, condition.getValorNumeric1()) == 0;
            case NEQ -> compare(value, condition.getValorNumeric1()) != 0;
            case BETWEEN -> compare(value, condition.getValorNumeric1()) >= 0
                    && compare(value, condition.getValorNumeric2()) <= 0;
            case OUTSIDE -> compare(value, condition.getValorNumeric1()) < 0
                    || compare(value, condition.getValorNumeric2()) > 0;
            case CONTAINS -> false;
        };
    }

    private static int compare(BigDecimal left, BigDecimal right) {
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private static boolean booleanMatches(EventoCondicao condition, Boolean value) {
        if (value == null || condition.getValorBoolean() == null) return false;
        return switch (condition.getOperador()) {
            case EQ -> value.equals(condition.getValorBoolean());
            case NEQ -> !value.equals(condition.getValorBoolean());
            default -> false;
        };
    }

    private static boolean textMatches(EventoCondicao condition, String value) {
        if (value == null || condition.getValorText() == null) return false;
        return switch (condition.getOperador()) {
            case EQ -> value.equals(condition.getValorText());
            case NEQ -> !value.equals(condition.getValorText());
            case CONTAINS -> value.contains(condition.getValorText());
            default -> false;
        };
    }

    private void validateDurationEvent(EventoDefinicao event) {
        if (event.getJanelaSegundos() == null || event.getJanelaSegundos() <= 0) {
            throw new IllegalArgumentException("janelaSegundos must be positive for DURACAO");
        }
        if (event.getDuracaoMinimaSegundos() == null || event.getDuracaoMinimaSegundos() <= 0) {
            throw new IllegalArgumentException("duracaoMinimaSegundos must be positive for DURACAO");
        }
        if (event.getDuracaoMinimaSegundos() > event.getJanelaSegundos()) {
            throw new IllegalArgumentException("duracaoMinimaSegundos cannot exceed janelaSegundos");
        }
    }

    private String snapshot(
            EventoDefinicao event,
            Medicao medicao,
            AcademicContext academicContext,
            Instant evaluationTime
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventDefinitionId", event.getId().toString());
        root.put("nome", event.getNome());
        root.put("tipoDisparo", event.getTipoDisparo().name());
        root.put("modoAvaliacao", event.getModoAvaliacao().name());
        root.put("janelaSegundos", event.getJanelaSegundos());
        root.put("duracaoMinimaSegundos", event.getDuracaoMinimaSegundos());
        root.put("medicaoInicialId", medicao.getId().toString());
        root.put("sensorExternalId", medicao.getSensor().getExternalId());
        root.put("compartimentoId", medicao.getSensor().getCompartimento().getId());
        root.put("inicioEm", medicao.getTimestamp().toString());
        root.put("evaluationTime", evaluationTime.toString());
        root.put("evaluatorVersion", "TEMPORAL_WINDOW_V1");
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

    private record TemporalConditionEvaluation(boolean matched, List<MeasurementFact> evidences) {}
}
