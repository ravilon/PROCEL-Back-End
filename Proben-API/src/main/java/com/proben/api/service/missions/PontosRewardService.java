package com.proben.api.service.missions;

import com.proben.api.entity.missions.AtividadeStatus;
import com.proben.api.entity.missions.PontosLancamento;
import com.proben.api.entity.missions.PontosLancamentoTipo;
import com.proben.api.exception.ConflictException;
import com.proben.api.exception.NotFoundException;
import com.proben.api.observability.ApiObservabilityMetrics;
import com.proben.api.repository.missions.PontosLancamentoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico que implementa as operacoes de negocio de pontos reward.
 */
@Service
public class PontosRewardService {

    private final PontosLancamentoRepository pontosLancamentoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ApiObservabilityMetrics metrics;

    public PontosRewardService(
            PontosLancamentoRepository pontosLancamentoRepository,
            JdbcTemplate jdbcTemplate,
            ApiObservabilityMetrics metrics
    ) {
        this.pontosLancamentoRepository = pontosLancamentoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    @Transactional
    public GrantResult grantAutomaticCompletionReward(UUID atividadeId, UUID eventoOcorrenciaId, Instant grantedAt) {
        if (atividadeId == null) throw new IllegalArgumentException("atividadeId is required");
        Instant effectiveGrantedAt = grantedAt == null ? Instant.now() : grantedAt;

        ActivityRewardData activity = loadActivityRewardData(atividadeId);
        if (activity.status() != AtividadeStatus.CONCLUIDA) {
            metrics.missionPointsFailure("activity_not_completed");
            throw new ConflictException("pontos reward requires completed activity id=" + atividadeId);
        }
        int pontos = activity.missaoPontos();
        if (pontos < 0) {
            metrics.missionPointsFailure("negative_mission_value");
            throw new ConflictException("Mission points must not be negative id=" + activity.missaoId());
        }
        if (pontos == 0) {
            return GrantResult.skippedZero(atividadeId);
        }

        ensureOccurrenceExists(eventoOcorrenciaId);
        String key = automaticCompletionKey(atividadeId);

        try {
            UUID insertedId = insertAutomaticGrant(activity, eventoOcorrenciaId, key, pontos, effectiveGrantedAt);
            if (insertedId != null) {
                PontosLancamento inserted = pontosLancamentoRepository.findById(insertedId)
                        .orElseThrow(() -> new NotFoundException("PontosLancamento not found id=" + insertedId));
                metrics.missionPointsGranted();
                metrics.missionPointsAmount(pontos);
                return GrantResult.created(inserted);
            }
        } catch (DataIntegrityViolationException ex) {
            metrics.missionPointsFailure("constraint_conflict");
            throw automaticGrantConflict(atividadeId, ex);
        }

        PontosLancamento existing = pontosLancamentoRepository.findByChaveIdempotencia(key)
                .orElseThrow(() -> new ConflictException("pontos idempotency key conflict without readable ledger entry"));
        validateEquivalent(existing, activity, eventoOcorrenciaId, pontos);
        metrics.missionPointsDuplicate();
        return GrantResult.duplicate(existing);
    }

    public static String automaticCompletionKey(UUID atividadeId) {
        if (atividadeId == null) throw new IllegalArgumentException("atividadeId is required");
        return "pontos:atividade:" + atividadeId + ":completion";
    }

    private UUID insertAutomaticGrant(
            ActivityRewardData activity,
            UUID eventoOcorrenciaId,
            String key,
            int pontos,
            Instant grantedAt
    ) {
        var rows = jdbcTemplate.query("""
                insert into pontos_lancamento (
                    pessoa_id,
                    atividade_id,
                    missao_id,
                    evento_ocorrencia_id,
                    tipo,
                    pontos,
                    chave_idempotencia,
                    descricao,
                    created_at,
                    created_by
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (chave_idempotencia) do nothing
                returning id
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                activity.pessoaId(),
                activity.atividadeId(),
                activity.missaoId(),
                eventoOcorrenciaId,
                PontosLancamentoTipo.CONCESSAO.name(),
                pontos,
                key,
                "Conclusao automatica da missao " + activity.missaoTitulo(),
                Timestamp.from(grantedAt),
                PontosLancamento.AUTO_COMPLETION_CREATED_BY
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ActivityRewardData loadActivityRewardData(UUID atividadeId) {
        var rows = jdbcTemplate.query("""
                select
                    a.id as atividade_id,
                    a.pessoa_id,
                    a.missao_id,
                    a.status,
                    m.titulo as missao_titulo,
                    m.pontos as missao_pontos
                from atividade a
                join missao m on m.id = a.missao_id
                where a.id = ?
                """,
                (rs, rowNum) -> new ActivityRewardData(
                        rs.getObject("atividade_id", UUID.class),
                        rs.getString("pessoa_id"),
                        rs.getObject("missao_id", UUID.class),
                        AtividadeStatus.valueOf(rs.getString("status")),
                        rs.getString("missao_titulo"),
                        rs.getInt("missao_pontos")
                ),
                atividadeId
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Atividade not found id=" + atividadeId);
        }
        return rows.getFirst();
    }

    private void ensureOccurrenceExists(UUID eventoOcorrenciaId) {
        if (eventoOcorrenciaId == null) {
            return;
        }
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from evento_ocorrencia where id = ?)",
                Boolean.class,
                eventoOcorrenciaId
        );
        if (!Boolean.TRUE.equals(exists)) {
            throw new NotFoundException("EventoOcorrencia not found id=" + eventoOcorrenciaId);
        }
    }

    private RuntimeException automaticGrantConflict(UUID atividadeId, DataIntegrityViolationException ex) {
        return pontosLancamentoRepository.findFirstByAtividadeIdAndTipoAndCreatedBy(
                        atividadeId,
                        PontosLancamentoTipo.CONCESSAO,
                        PontosLancamento.AUTO_COMPLETION_CREATED_BY
                )
                .map(existing -> new ConflictException("Automatic completion pontos already exists with divergent idempotency key for activity id=" + atividadeId))
                .orElse(new ConflictException("pontos ledger constraint conflict for activity id=" + atividadeId));
    }

    private static void validateEquivalent(
            PontosLancamento existing,
            ActivityRewardData activity,
            UUID eventoOcorrenciaId,
            int pontos
    ) {
        if (!Objects.equals(existing.getPessoa().getId(), activity.pessoaId())
                || !Objects.equals(existing.getAtividade().getId(), activity.atividadeId())
                || !Objects.equals(existing.getMissao().getId(), activity.missaoId())
                || !Objects.equals(eventId(existing), eventoOcorrenciaId)
                || existing.getPontos() != pontos
                || existing.getTipo() != PontosLancamentoTipo.CONCESSAO) {
            throw new ConflictException("pontos idempotency key reused with different reward content");
        }
    }

    private static UUID eventId(PontosLancamento lancamento) {
        return lancamento.getEventoOcorrencia() == null ? null : lancamento.getEventoOcorrencia().getId();
    }

    private record ActivityRewardData(
            UUID atividadeId,
            String pessoaId,
            UUID missaoId,
            AtividadeStatus status,
            String missaoTitulo,
            int missaoPontos
    ) {
    }

    public record GrantResult(
            boolean created,
            boolean duplicate,
            boolean skippedZeroValue,
            PontosLancamento lancamento
    ) {
        static GrantResult created(PontosLancamento lancamento) {
            return new GrantResult(true, false, false, lancamento);
        }

        static GrantResult duplicate(PontosLancamento lancamento) {
            return new GrantResult(false, true, false, lancamento);
        }

        static GrantResult skippedZero(UUID atividadeId) {
            return new GrantResult(false, false, true, null);
        }
    }
}
