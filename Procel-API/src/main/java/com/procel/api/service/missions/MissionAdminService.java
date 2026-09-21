package com.procel.api.service.missions;

import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.config.MissionRuleEngineProperties;
import com.procel.api.dto.missions.MissionAdminDTOs;
import com.procel.api.entity.missions.EventoAvaliacaoRequest;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoJanelaAvaliacao;
import com.procel.api.entity.missions.EventoJanelaEvidencia;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoOcorrenciaEvidencia;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.repository.missions.EventoAvaliacaoRequestRepository;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.repository.missions.EventoJanelaAvaliacaoRepository;
import com.procel.api.repository.missions.EventoJanelaEvidenciaRepository;
import com.procel.api.repository.missions.EventoOcorrenciaEvidenciaRepository;
import com.procel.api.repository.missions.EventoOcorrenciaRepository;
import com.procel.api.service.missions.evaluation.MissionEventEvaluationWorker;
import com.procel.api.service.missions.evaluation.MissionTemporalWindowWorker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MissionAdminService {
    private static final int MAX_PAGE_SIZE = 100;

    private final EventoDefinicaoRepository eventoDefinicaoRepository;
    private final EventoAvaliacaoRequestRepository requestRepository;
    private final EventoJanelaAvaliacaoRepository janelaRepository;
    private final EventoJanelaEvidenciaRepository janelaEvidenciaRepository;
    private final EventoOcorrenciaRepository ocorrenciaRepository;
    private final EventoOcorrenciaEvidenciaRepository ocorrenciaEvidenciaRepository;
    private final EventoJanelaAvaliacaoService janelaService;
    private final EventoOcorrenciaService ocorrenciaService;
    private final EventoAvaliacaoRequestService requestService;
    private final MissionEventEvaluationWorker evaluationWorker;
    private final MissionTemporalWindowWorker temporalWindowWorker;
    private final MissionEvaluationProperties evaluationProperties;
    private final MissionRuleEngineProperties ruleEngineProperties;

    public MissionAdminService(
            EventoDefinicaoRepository eventoDefinicaoRepository,
            EventoAvaliacaoRequestRepository requestRepository,
            EventoJanelaAvaliacaoRepository janelaRepository,
            EventoJanelaEvidenciaRepository janelaEvidenciaRepository,
            EventoOcorrenciaRepository ocorrenciaRepository,
            EventoOcorrenciaEvidenciaRepository ocorrenciaEvidenciaRepository,
            EventoJanelaAvaliacaoService janelaService,
            EventoOcorrenciaService ocorrenciaService,
            EventoAvaliacaoRequestService requestService,
            MissionEventEvaluationWorker evaluationWorker,
            MissionTemporalWindowWorker temporalWindowWorker,
            MissionEvaluationProperties evaluationProperties,
            MissionRuleEngineProperties ruleEngineProperties
    ) {
        this.eventoDefinicaoRepository = eventoDefinicaoRepository;
        this.requestRepository = requestRepository;
        this.janelaRepository = janelaRepository;
        this.janelaEvidenciaRepository = janelaEvidenciaRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.ocorrenciaEvidenciaRepository = ocorrenciaEvidenciaRepository;
        this.janelaService = janelaService;
        this.ocorrenciaService = ocorrenciaService;
        this.requestService = requestService;
        this.evaluationWorker = evaluationWorker;
        this.temporalWindowWorker = temporalWindowWorker;
        this.evaluationProperties = evaluationProperties;
        this.ruleEngineProperties = ruleEngineProperties;
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.EventDefinitionSummaryResponse> listEvents(
            MissionAdminDTOs.EventFilter filter,
            int page,
            int size
    ) {
        Page<EventoDefinicao> events = eventoDefinicaoRepository.findAll(
                eventSpecification(filter),
                pageable(page, size, Sort.by(Sort.Order.asc("ordem"), Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
        );
        return pageResponse(events.map(this::toEventSummary));
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.EvaluationRequestResponse> listRequests(
            MissionAdminDTOs.RequestFilter filter,
            int page,
            int size
    ) {
        Page<EventoAvaliacaoRequest> requests = requestRepository.findAll(
                requestSpecification(filter),
                pageable(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        return pageResponse(requests.map(this::toRequestResponse));
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.WindowResponse> listWindows(
            MissionAdminDTOs.WindowFilter filter,
            int page,
            int size
    ) {
        Page<EventoJanelaAvaliacao> windows = janelaRepository.findAll(
                windowSpecification(filter),
                pageable(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        return pageResponse(windows.map(this::toWindowResponse));
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.WindowResponse getWindow(UUID id) {
        return toWindowResponse(janelaService.buscar(id));
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.WindowEvidenceResponse> listWindowEvidence(UUID windowId, int page, int size) {
        janelaService.buscar(windowId);
        Page<EventoJanelaEvidencia> evidences = janelaEvidenciaRepository.findByJanelaAvaliacaoId(
                windowId,
                pageable(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
        );
        return pageResponse(evidences.map(this::toWindowEvidenceResponse));
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.OccurrenceResponse> listOccurrences(
            MissionAdminDTOs.OccurrenceFilter filter,
            int page,
            int size
    ) {
        Page<EventoOcorrencia> occurrences = ocorrenciaRepository.findAll(
                occurrenceSpecification(filter),
                pageable(page, size, Sort.by(Sort.Order.desc("detectadoEm"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        return pageResponse(occurrences.map(this::toOccurrenceResponse));
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.OccurrenceResponse getOccurrence(UUID id) {
        return toOccurrenceResponse(ocorrenciaService.buscar(id));
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.OccurrenceEvidenceResponse> listOccurrenceEvidence(UUID occurrenceId, int page, int size) {
        ocorrenciaService.buscar(occurrenceId);
        Page<EventoOcorrenciaEvidencia> evidences = ocorrenciaEvidenciaRepository.findByEventoOcorrenciaId(
                occurrenceId,
                pageable(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
        );
        return pageResponse(evidences.map(this::toOccurrenceEvidenceResponse));
    }

    @Transactional
    public MissionAdminDTOs.WindowResponse retryWindow(UUID windowId, Instant retryAt, String reason) {
        janelaService.marcarRetry(windowId, retryAt == null ? Instant.now() : retryAt, reason);
        return toWindowResponse(janelaService.buscar(windowId));
    }

    @Transactional
    public MissionAdminDTOs.WindowResponse satisfyWindow(UUID windowId) {
        janelaService.satisfazer(windowId);
        return toWindowResponse(janelaService.buscar(windowId));
    }

    @Transactional
    public MissionAdminDTOs.WindowResponse invalidateWindow(UUID windowId, String reason) {
        janelaService.invalidar(windowId, reason);
        return toWindowResponse(janelaService.buscar(windowId));
    }

    @Transactional
    public MissionAdminDTOs.WindowResponse expireWindow(UUID windowId, String reason) {
        janelaService.expirar(windowId, reason);
        return toWindowResponse(janelaService.buscar(windowId));
    }

    @Transactional
    public MissionAdminDTOs.WindowResponse failWindow(UUID windowId, String reason) {
        janelaService.marcarFailed(windowId, reason);
        return toWindowResponse(janelaService.buscar(windowId));
    }

    @Transactional
    public MissionAdminDTOs.OccurrenceResponse updateOccurrenceStatus(UUID occurrenceId, EventoOcorrenciaStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        return toOccurrenceResponse(ocorrenciaService.atualizarStatus(occurrenceId, status));
    }

    @Transactional(readOnly = true)
    public MissionAdminDTOs.WorkerStatusResponse workerStatus() {
        MissionEvaluationProperties.TemporalWindows temporal = evaluationProperties.getTemporalWindows();
        MissionRuleEngineProperties.DroolsProperties drools = ruleEngineProperties.drools();
        return new MissionAdminDTOs.WorkerStatusResponse(
                new MissionAdminDTOs.WorkerState(
                        evaluationProperties.isWorkerEnabled(),
                        evaluationProperties.getFixedDelay(),
                        evaluationProperties.getBatchSize(),
                        evaluationProperties.getLeaseDuration(),
                        evaluationProperties.getMaxAttempts(),
                        requestService.countBacklog()
                ),
                new MissionAdminDTOs.WorkerState(
                        temporal.isWorkerEnabled(),
                        temporal.getFixedDelay(),
                        temporal.getBatchSize(),
                        temporal.getLeaseDuration(),
                        temporal.getMaxAttempts(),
                        janelaService.countBacklog()
                ),
                new MissionAdminDTOs.DroolsState(
                        ruleEngineProperties.ruleEngine().name(),
                        temporal.isDroolsEnabled(),
                        temporal.isActivitiesEnabled(),
                        drools.maxFactsPerEvaluation(),
                        drools.maxCacheEntries(),
                        drools.cacheExpiration(),
                        drools.evaluationTimeout(),
                        drools.maximumSampleGap()
                )
        );
    }

    public MissionAdminDTOs.WorkerRunResponse runEvaluationWorker() {
        return new MissionAdminDTOs.WorkerRunResponse("evaluation", evaluationWorker.processAvailableBatch(true));
    }

    public MissionAdminDTOs.WorkerRunResponse runTemporalWindowWorker() {
        return new MissionAdminDTOs.WorkerRunResponse("temporal-windows", temporalWindowWorker.processAvailableBatch());
    }

    private Specification<EventoDefinicao> eventSpecification(MissionAdminDTOs.EventFilter filter) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (filter == null) {
                return predicate;
            }
            if (filter.missionId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("missao").get("id"), filter.missionId()));
            }
            if (filter.active() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("ativo"), filter.active()));
            }
            if (filter.tipoDisparo() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("tipoDisparo"), filter.tipoDisparo()));
            }
            if (filter.modoAvaliacao() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("modoAvaliacao"), filter.modoAvaliacao()));
            }
            return predicate;
        };
    }

    private Specification<EventoAvaliacaoRequest> requestSpecification(MissionAdminDTOs.RequestFilter filter) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (filter == null) {
                return predicate;
            }
            if (filter.status() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), filter.status()));
            }
            if (filter.medicaoId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("medicao").get("id"), filter.medicaoId()));
            }
            return predicate;
        };
    }

    private Specification<EventoJanelaAvaliacao> windowSpecification(MissionAdminDTOs.WindowFilter filter) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (filter == null) {
                return predicate;
            }
            if (filter.status() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), filter.status()));
            }
            if (filter.eventoDefinicaoId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("eventoDefinicao").get("id"), filter.eventoDefinicaoId()));
            }
            if (filter.compartimentoId() != null && !filter.compartimentoId().isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("compartimento").get("id"), filter.compartimentoId()));
            }
            if (filter.periodoAulaId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("periodoAula").get("id"), filter.periodoAulaId()));
            }
            return predicate;
        };
    }

    private Specification<EventoOcorrencia> occurrenceSpecification(MissionAdminDTOs.OccurrenceFilter filter) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (filter == null) {
                return predicate;
            }
            if (filter.status() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), filter.status()));
            }
            if (filter.eventoDefinicaoId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("eventoDefinicao").get("id"), filter.eventoDefinicaoId()));
            }
            if (filter.compartimentoId() != null && !filter.compartimentoId().isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("compartimento").get("id"), filter.compartimentoId()));
            }
            if (filter.periodoAulaId() != null) {
                predicate = cb.and(predicate, cb.equal(root.get("periodoAula").get("id"), filter.periodoAulaId()));
            }
            return predicate;
        };
    }

    private Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), sort);
    }

    private <T> MissionAdminDTOs.PageResponse<T> pageResponse(Page<T> page) {
        return new MissionAdminDTOs.PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private MissionAdminDTOs.EventDefinitionSummaryResponse toEventSummary(EventoDefinicao event) {
        return new MissionAdminDTOs.EventDefinitionSummaryResponse(
                event.getId(),
                event.getMissao().getId(),
                event.getMissao().getTitulo(),
                event.getNome(),
                event.getTipoDisparo(),
                event.getModoAvaliacao(),
                event.isAtivo(),
                event.getOrdem(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    private MissionAdminDTOs.EvaluationRequestResponse toRequestResponse(EventoAvaliacaoRequest request) {
        return new MissionAdminDTOs.EvaluationRequestResponse(
                request.getId(),
                request.getMedicao().getId(),
                request.getStatus(),
                request.getAttempts(),
                request.getAvailableAt(),
                request.getClaimedAt(),
                request.getLeaseUntil(),
                request.getProcessedAt(),
                request.getLastError(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }

    private MissionAdminDTOs.WindowResponse toWindowResponse(EventoJanelaAvaliacao window) {
        return new MissionAdminDTOs.WindowResponse(
                window.getId(),
                window.getEventoDefinicao().getId(),
                window.getEventoDefinicao().getNome(),
                window.getCompartimento().getId(),
                window.getPeriodoAula() == null ? null : window.getPeriodoAula().getId(),
                window.getStatus(),
                window.getInicioEm(),
                window.getFimPrevistoEm(),
                window.getUltimaMedicaoEm(),
                window.getProximaAvaliacaoEm(),
                window.getLeaseUntil(),
                window.getAttempts(),
                window.getChaveIdempotencia(),
                window.getContextoSnapshot(),
                window.getLastError(),
                window.getCreatedAt(),
                window.getUpdatedAt()
        );
    }

    private MissionAdminDTOs.WindowEvidenceResponse toWindowEvidenceResponse(EventoJanelaEvidencia evidence) {
        return new MissionAdminDTOs.WindowEvidenceResponse(
                evidence.getId(),
                evidence.getJanelaAvaliacao().getId(),
                evidence.getMedicao().getId(),
                evidence.getParametroValor() == null ? null : evidence.getParametroValor().getId(),
                evidence.getPapel(),
                evidence.getCreatedAt()
        );
    }

    private MissionAdminDTOs.OccurrenceResponse toOccurrenceResponse(EventoOcorrencia occurrence) {
        return new MissionAdminDTOs.OccurrenceResponse(
                occurrence.getId(),
                occurrence.getEventoDefinicao().getId(),
                occurrence.getEventoDefinicao().getNome(),
                occurrence.getCompartimento().getId(),
                occurrence.getPeriodoAula() == null ? null : occurrence.getPeriodoAula().getId(),
                occurrence.getSensor() == null ? null : occurrence.getSensor().getExternalId(),
                occurrence.getStatus(),
                occurrence.getInicioEm(),
                occurrence.getFimEm(),
                occurrence.getDetectadoEm(),
                occurrence.getChaveIdempotencia(),
                occurrence.getContextoSnapshot(),
                occurrence.getConteudoFingerprint(),
                occurrence.getCreatedAt(),
                occurrence.getUpdatedAt()
        );
    }

    private MissionAdminDTOs.OccurrenceEvidenceResponse toOccurrenceEvidenceResponse(EventoOcorrenciaEvidencia evidence) {
        return new MissionAdminDTOs.OccurrenceEvidenceResponse(
                evidence.getId(),
                evidence.getEventoOcorrencia().getId(),
                evidence.getMedicao().getId(),
                evidence.getParametroValor() == null ? null : evidence.getParametroValor().getId(),
                evidence.getPapel(),
                evidence.getCreatedAt()
        );
    }
}
