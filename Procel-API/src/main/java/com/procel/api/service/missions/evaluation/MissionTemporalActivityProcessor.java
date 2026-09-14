package com.procel.api.service.missions.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.entity.missions.EventoJanelaAvaliacao;
import com.procel.api.entity.missions.EventoJanelaAvaliacaoStatus;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.missions.EventoJanelaAvaliacaoRepository;
import com.procel.api.repository.missions.EventoOcorrenciaRepository;
import com.procel.api.service.academic.AcademicContext;
import com.procel.api.service.missions.EventoOcorrenciaService;
import com.procel.api.service.missions.MissionEventActivityProcessingException;
import com.procel.api.service.missions.MissionEventActivityProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MissionTemporalActivityProcessor {
    private final MissionEvaluationProperties properties;
    private final EventoOcorrenciaRepository ocorrenciaRepository;
    private final EventoJanelaAvaliacaoRepository janelaRepository;
    private final EventoOcorrenciaService ocorrenciaService;
    private final MissionEventActivityProcessor activityProcessor;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MissionTemporalActivityProcessor(
            MissionEvaluationProperties properties,
            EventoOcorrenciaRepository ocorrenciaRepository,
            EventoJanelaAvaliacaoRepository janelaRepository,
            EventoOcorrenciaService ocorrenciaService,
            MissionEventActivityProcessor activityProcessor,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.janelaRepository = janelaRepository;
        this.ocorrenciaService = ocorrenciaService;
        this.activityProcessor = activityProcessor;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return properties.getTemporalWindows().isActivitiesEnabled();
    }

    @Transactional(readOnly = true)
    public List<UUID> findPendingSatisfiedOccurrences(int limit) {
        if (!enabled()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                select o.id
                from evento_ocorrencia o
                join evento_definicao e on e.id = o.evento_definicao_id
                join evento_janela_avaliacao j
                  on o.chave_idempotencia = concat('event:', e.id, ':window:', j.id)
                where o.status = 'CONFIRMADO'
                  and (
                        (e.tipo_disparo = 'MEDICAO_RECEBIDA' and e.modo_avaliacao = 'DURACAO')
                        or (e.tipo_disparo = 'JANELA_ENCERRADA' and e.modo_avaliacao = 'INSTANTANEO')
                  )
                  and j.status = 'SATISFEITA'
                order by o.detectado_em asc, o.id asc
                limit ?
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), Math.max(1, limit));
    }

    @Transactional
    public ProcessTemporalActivityOutcome processSatisfiedOccurrence(UUID occurrenceId, Instant processedAt) {
        if (!enabled()) {
            return ProcessTemporalActivityOutcome.skipped("Temporal activities are disabled");
        }
        EventoOcorrencia occurrence = ocorrenciaRepository.findById(occurrenceId)
                .orElseThrow(() -> new NotFoundException("EventoOcorrencia not found id=" + occurrenceId));
        if (occurrence.getStatus() == EventoOcorrenciaStatus.PROCESSADO) {
            return ProcessTemporalActivityOutcome.processedOutcome();
        }
        if (occurrence.getStatus() != EventoOcorrenciaStatus.CONFIRMADO) {
            throw permanent("Only CONFIRMADO temporal occurrences can be processed id=" + occurrenceId);
        }
        validateTemporalEvent(occurrence);

        UUID windowId = windowIdFromOccurrenceSnapshot(occurrence);
        EventoJanelaAvaliacao window = janelaRepository.findById(windowId)
                .orElseThrow(() -> new NotFoundException("EventoJanelaAvaliacao not found id=" + windowId));
        validateSatisfiedWindow(occurrence, window);

        AcademicContext academicContext = academicContextFromWindowSnapshot(window);
        activityProcessor.process(
                occurrence.getId(),
                academicContext.empty() ? Optional.empty() : Optional.of(academicContext),
                Optional.empty(),
                processedAt == null ? Instant.now() : processedAt
        );
        return ProcessTemporalActivityOutcome.processedOutcome();
    }

    @Transactional
    public void invalidateOccurrence(UUID occurrenceId, String reason) {
        EventoOcorrencia occurrence = ocorrenciaRepository.findById(occurrenceId)
                .orElseThrow(() -> new NotFoundException("EventoOcorrencia not found id=" + occurrenceId));
        if (occurrence.getStatus() == EventoOcorrenciaStatus.CONFIRMADO) {
            ocorrenciaService.atualizarStatus(occurrenceId, EventoOcorrenciaStatus.INVALIDADO);
        }
    }

    private void validateTemporalEvent(EventoOcorrencia occurrence) {
        var event = occurrence.getEventoDefinicao();
        boolean duration = event.getTipoDisparo() == EventoTipoDisparo.MEDICAO_RECEBIDA
                && event.getModoAvaliacao() == EventoModoAvaliacao.DURACAO;
        boolean windowClosed = event.getTipoDisparo() == EventoTipoDisparo.JANELA_ENCERRADA
                && event.getModoAvaliacao() == EventoModoAvaliacao.INSTANTANEO;
        if (!duration && !windowClosed) {
            throw permanent("Occurrence is not a supported temporal occurrence id=" + occurrence.getId());
        }
    }

    private UUID windowIdFromOccurrenceSnapshot(EventoOcorrencia occurrence) {
        try {
            JsonNode snapshot = objectMapper.readTree(occurrence.getContextoSnapshot());
            JsonNode value = snapshot.get("janelaAvaliacaoId");
            if (value == null || value.isNull() || value.asText().isBlank()) {
                throw permanent("Temporal occurrence snapshot has no janelaAvaliacaoId id=" + occurrence.getId());
            }
            return UUID.fromString(value.asText());
        } catch (MissionEventActivityProcessingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw permanent("Invalid temporal occurrence snapshot id=" + occurrence.getId(), ex);
        }
    }

    private void validateSatisfiedWindow(EventoOcorrencia occurrence, EventoJanelaAvaliacao window) {
        WindowValidationData current = currentWindowData(window.getId());
        if (current.status() != EventoJanelaAvaliacaoStatus.SATISFEITA) {
            throw permanent("Temporal occurrence window is not SATISFEITA id=" + window.getId());
        }
        if (!current.eventoDefinicaoId().equals(occurrence.getEventoDefinicao().getId())) {
            throw permanent("Temporal occurrence window belongs to another event id=" + window.getId());
        }
        if (!current.compartimentoId().equals(occurrence.getCompartimento().getId())) {
            throw permanent("Temporal occurrence window belongs to another compartment id=" + window.getId());
        }
        String expectedKey = "event:" + occurrence.getEventoDefinicao().getId() + ":window:" + window.getId();
        if (!expectedKey.equals(occurrence.getChaveIdempotencia())) {
            throw permanent("Temporal occurrence idempotency key does not match satisfied window id=" + occurrence.getId());
        }
    }

    private WindowValidationData currentWindowData(UUID windowId) {
        var rows = jdbcTemplate.query("""
                select status, evento_definicao_id, compartimento_id
                from evento_janela_avaliacao
                where id = ?
                """,
                (rs, rowNum) -> new WindowValidationData(
                        EventoJanelaAvaliacaoStatus.valueOf(rs.getString("status")),
                        rs.getObject("evento_definicao_id", UUID.class),
                        rs.getString("compartimento_id")
                ),
                windowId);
        if (rows.isEmpty()) {
            throw new NotFoundException("EventoJanelaAvaliacao not found id=" + windowId);
        }
        return rows.getFirst();
    }

    private AcademicContext academicContextFromWindowSnapshot(EventoJanelaAvaliacao window) {
        try {
            JsonNode snapshot = objectMapper.readTree(window.getContextoSnapshot());
            List<String> pessoaIds = new ArrayList<>();
            JsonNode people = snapshot.get("eligiblePersonIds");
            if (people != null && people.isArray()) {
                people.forEach(node -> {
                    if (!node.isNull() && !node.asText().isBlank()) {
                        pessoaIds.add(node.asText());
                    }
                });
            }
            UUID periodoAulaId = textUuid(snapshot, "periodoAulaId").orElse(null);
            Long disciplinaId = optionalLong(snapshot, "disciplinaId").orElse(null);
            String turma = optionalText(snapshot, "turma").orElse(null);
            String periodoLetivo = optionalText(snapshot, "periodoLetivo").orElse(null);
            LocalDateTime inicio = LocalDateTime.ofInstant(window.getInicioEm(), properties.getAcademicZone());
            LocalDateTime fim = LocalDateTime.ofInstant(window.getFimPrevistoEm(), properties.getAcademicZone());
            return new AcademicContext(
                    periodoAulaId,
                    disciplinaId,
                    turma,
                    periodoLetivo,
                    window.getCompartimento().getId(),
                    inicio,
                    fim,
                    pessoaIds
            );
        } catch (Exception ex) {
            throw permanent("Invalid temporal window academic snapshot id=" + window.getId(), ex);
        }
    }

    private Optional<UUID> textUuid(JsonNode node, String field) {
        return optionalText(node, field).map(UUID::fromString);
    }

    private Optional<String> optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.asText());
    }

    private Optional<Long> optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        return Optional.of(value.asLong());
    }

    private MissionEventActivityProcessingException permanent(String message) {
        return new MissionEventActivityProcessingException(message, true);
    }

    private MissionEventActivityProcessingException permanent(String message, Exception cause) {
        return new MissionEventActivityProcessingException(message, true, cause);
    }

    public record ProcessTemporalActivityOutcome(boolean processed, boolean skipped, String reason) {
        static ProcessTemporalActivityOutcome processedOutcome() {
            return new ProcessTemporalActivityOutcome(true, false, "Processed");
        }

        static ProcessTemporalActivityOutcome skipped(String reason) {
            return new ProcessTemporalActivityOutcome(false, true, reason);
        }
    }

    private record WindowValidationData(
            EventoJanelaAvaliacaoStatus status,
            UUID eventoDefinicaoId,
            String compartimentoId
    ) {
    }
}
