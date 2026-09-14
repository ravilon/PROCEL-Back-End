package com.procel.api.dto.missions;

import com.procel.api.entity.missions.EventoAvaliacaoRequestStatus;
import com.procel.api.entity.missions.EventoJanelaAvaliacaoStatus;
import com.procel.api.entity.missions.EventoJanelaEvidenciaPapel;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOcorrenciaEvidenciaPapel;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.entity.missions.EventoTipoDisparo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MissionAdminDTOs {
    private MissionAdminDTOs() {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record EventFilter(
            UUID missionId,
            Boolean active,
            EventoTipoDisparo tipoDisparo,
            EventoModoAvaliacao modoAvaliacao
    ) {}

    public record RequestFilter(
            EventoAvaliacaoRequestStatus status,
            UUID medicaoId
    ) {}

    public record WindowFilter(
            EventoJanelaAvaliacaoStatus status,
            UUID eventoDefinicaoId,
            String compartimentoId,
            UUID periodoAulaId
    ) {}

    public record OccurrenceFilter(
            EventoOcorrenciaStatus status,
            UUID eventoDefinicaoId,
            String compartimentoId,
            UUID periodoAulaId
    ) {}

    @Schema(description = "Comando administrativo de transicao de janela.")
    public record WindowOperationRequest(
            String reason,
            Instant retryAt
    ) {}

    @Schema(description = "Comando administrativo de transicao de ocorrencia.")
    public record OccurrenceStatusRequest(
            EventoOcorrenciaStatus status
    ) {}

    public record EventDefinitionSummaryResponse(
            UUID id,
            UUID missaoId,
            String missaoTitulo,
            String nome,
            EventoTipoDisparo tipoDisparo,
            EventoModoAvaliacao modoAvaliacao,
            boolean ativo,
            Integer ordem,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record EvaluationRequestResponse(
            UUID id,
            UUID medicaoId,
            EventoAvaliacaoRequestStatus status,
            int attempts,
            Instant availableAt,
            Instant claimedAt,
            Instant leaseUntil,
            Instant processedAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record WindowResponse(
            UUID id,
            UUID eventoDefinicaoId,
            String eventoNome,
            String compartimentoId,
            UUID periodoAulaId,
            EventoJanelaAvaliacaoStatus status,
            Instant inicioEm,
            Instant fimPrevistoEm,
            Instant ultimaMedicaoEm,
            Instant proximaAvaliacaoEm,
            Instant leaseUntil,
            int attempts,
            String chaveIdempotencia,
            String contextoSnapshot,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record WindowEvidenceResponse(
            UUID id,
            UUID janelaId,
            UUID medicaoId,
            UUID parametroValorId,
            EventoJanelaEvidenciaPapel papel,
            Instant createdAt
    ) {}

    public record OccurrenceResponse(
            UUID id,
            UUID eventoDefinicaoId,
            String eventoNome,
            String compartimentoId,
            UUID periodoAulaId,
            String sensorExternalId,
            EventoOcorrenciaStatus status,
            Instant inicioEm,
            Instant fimEm,
            Instant detectadoEm,
            String chaveIdempotencia,
            String contextoSnapshot,
            String conteudoFingerprint,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record OccurrenceEvidenceResponse(
            UUID id,
            UUID ocorrenciaId,
            UUID medicaoId,
            UUID parametroValorId,
            EventoOcorrenciaEvidenciaPapel papel,
            Instant createdAt
    ) {}

    public record WorkerStatusResponse(
            WorkerState evaluation,
            WorkerState temporalWindows,
            DroolsState drools
    ) {}

    public record WorkerState(
            boolean enabled,
            Duration fixedDelay,
            int batchSize,
            Duration leaseDuration,
            int maxAttempts,
            long backlog
    ) {}

    public record DroolsState(
            String selectedRuleEngine,
            boolean temporalDroolsEnabled,
            boolean temporalActivitiesEnabled,
            int maxFactsPerEvaluation,
            int maxCacheEntries,
            Duration cacheExpiration,
            Duration evaluationTimeout,
            Duration maximumSampleGap
    ) {}

    public record WorkerRunResponse(
            String worker,
            int processed
    ) {}
}
