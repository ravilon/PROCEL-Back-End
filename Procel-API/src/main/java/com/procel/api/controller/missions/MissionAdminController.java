package com.procel.api.controller.missions;

import com.procel.api.dto.missions.MissionAdminDTOs;
import com.procel.api.entity.missions.EventoAvaliacaoRequestStatus;
import com.procel.api.entity.missions.EventoJanelaAvaliacaoStatus;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.service.missions.MissionAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/missions")
@Tag(name = "Mission Admin", description = "Consulta e operacao administrativa do motor persistente de eventos de missoes.")
public class MissionAdminController {

    private final MissionAdminService service;

    public MissionAdminController(MissionAdminService service) {
        this.service = service;
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista definicoes de eventos", description = "Consulta administrativa; nao avalia regras nem cria efeitos.")
    @ApiResponse(responseCode = "200", description = "Eventos retornados.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.EventDefinitionSummaryResponse> listEvents(
            @RequestParam(required = false) UUID missionId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) EventoTipoDisparo tipoDisparo,
            @RequestParam(required = false) EventoModoAvaliacao modoAvaliacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listEvents(
                new MissionAdminDTOs.EventFilter(missionId, active, tipoDisparo, modoAvaliacao),
                page,
                size
        );
    }

    @GetMapping("/evaluation-requests")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista requests de avaliacao de eventos")
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.EvaluationRequestResponse> listRequests(
            @RequestParam(required = false) EventoAvaliacaoRequestStatus status,
            @RequestParam(required = false) UUID medicaoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listRequests(new MissionAdminDTOs.RequestFilter(status, medicaoId), page, size);
    }

    @GetMapping("/windows")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista janelas temporais persistidas")
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.WindowResponse> listWindows(
            @RequestParam(required = false) EventoJanelaAvaliacaoStatus status,
            @RequestParam(required = false) UUID eventoDefinicaoId,
            @RequestParam(required = false) String compartimentoId,
            @RequestParam(required = false) UUID periodoAulaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listWindows(
                new MissionAdminDTOs.WindowFilter(status, eventoDefinicaoId, compartimentoId, periodoAulaId),
                page,
                size
        );
    }

    @GetMapping("/windows/{windowId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Busca janela temporal")
    public MissionAdminDTOs.WindowResponse getWindow(
            @Parameter(description = "ID da janela.") @PathVariable UUID windowId
    ) {
        return service.getWindow(windowId);
    }

    @GetMapping("/windows/{windowId}/evidences")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista evidencias da janela")
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.WindowEvidenceResponse> listWindowEvidence(
            @PathVariable UUID windowId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.listWindowEvidence(windowId, page, size);
    }

    @PostMapping("/windows/{windowId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Libera uma janela PROCESSING para retry")
    public MissionAdminDTOs.WindowResponse retryWindow(
            @PathVariable UUID windowId,
            @RequestBody(required = false) MissionAdminDTOs.WindowOperationRequest request
    ) {
        return service.retryWindow(windowId, request == null ? null : request.retryAt(), request == null ? null : request.reason());
    }

    @PostMapping("/windows/{windowId}/satisfy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Marca janela como SATISFEITA usando a transicao persistente existente")
    public MissionAdminDTOs.WindowResponse satisfyWindow(@PathVariable UUID windowId) {
        return service.satisfyWindow(windowId);
    }

    @PostMapping("/windows/{windowId}/invalidate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Marca janela como INVALIDADA")
    public MissionAdminDTOs.WindowResponse invalidateWindow(
            @PathVariable UUID windowId,
            @RequestBody(required = false) MissionAdminDTOs.WindowOperationRequest request
    ) {
        return service.invalidateWindow(windowId, request == null ? null : request.reason());
    }

    @PostMapping("/windows/{windowId}/expire")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Marca janela como EXPIRADA")
    public MissionAdminDTOs.WindowResponse expireWindow(
            @PathVariable UUID windowId,
            @RequestBody(required = false) MissionAdminDTOs.WindowOperationRequest request
    ) {
        return service.expireWindow(windowId, request == null ? null : request.reason());
    }

    @PostMapping("/windows/{windowId}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Marca janela como FAILED")
    public MissionAdminDTOs.WindowResponse failWindow(
            @PathVariable UUID windowId,
            @RequestBody(required = false) MissionAdminDTOs.WindowOperationRequest request
    ) {
        return service.failWindow(windowId, request == null ? null : request.reason());
    }

    @GetMapping("/occurrences")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista ocorrencias de eventos")
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.OccurrenceResponse> listOccurrences(
            @RequestParam(required = false) EventoOcorrenciaStatus status,
            @RequestParam(required = false) UUID eventoDefinicaoId,
            @RequestParam(required = false) String compartimentoId,
            @RequestParam(required = false) UUID periodoAulaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listOccurrences(
                new MissionAdminDTOs.OccurrenceFilter(status, eventoDefinicaoId, compartimentoId, periodoAulaId),
                page,
                size
        );
    }

    @GetMapping("/occurrences/{occurrenceId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Busca ocorrencia de evento")
    public MissionAdminDTOs.OccurrenceResponse getOccurrence(@PathVariable UUID occurrenceId) {
        return service.getOccurrence(occurrenceId);
    }

    @GetMapping("/occurrences/{occurrenceId}/evidences")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista evidencias da ocorrencia")
    public MissionAdminDTOs.PageResponse<MissionAdminDTOs.OccurrenceEvidenceResponse> listOccurrenceEvidence(
            @PathVariable UUID occurrenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.listOccurrenceEvidence(occurrenceId, page, size);
    }

    @PostMapping("/occurrences/{occurrenceId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza status de uma ocorrencia usando as transicoes existentes")
    public MissionAdminDTOs.OccurrenceResponse updateOccurrenceStatus(
            @PathVariable UUID occurrenceId,
            @RequestBody MissionAdminDTOs.OccurrenceStatusRequest request
    ) {
        return service.updateOccurrenceStatus(occurrenceId, request == null ? null : request.status());
    }

    @GetMapping("/workers/status")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Consulta flags e backlog dos workers de eventos")
    public MissionAdminDTOs.WorkerStatusResponse workerStatus() {
        return service.workerStatus();
    }

    @PostMapping("/workers/evaluation/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Executa um lote do worker instantaneo respeitando as flags atuais")
    public MissionAdminDTOs.WorkerRunResponse runEvaluationWorker() {
        return service.runEvaluationWorker();
    }

    @PostMapping("/workers/temporal-windows/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Executa um lote do worker temporal respeitando as flags atuais")
    public MissionAdminDTOs.WorkerRunResponse runTemporalWindowWorker() {
        return service.runTemporalWindowWorker();
    }
}
