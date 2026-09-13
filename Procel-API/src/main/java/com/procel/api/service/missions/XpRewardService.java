package com.procel.api.service.missions;

import com.procel.api.entity.missions.AtividadeStatus;
import com.procel.api.entity.missions.XpLancamento;
import com.procel.api.entity.missions.XpLancamentoTipo;
import com.procel.api.exception.ConflictException;
import com.procel.api.exception.NotFoundException;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.repository.missions.XpLancamentoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class XpRewardService {

    private final XpLancamentoRepository xpLancamentoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ApiObservabilityMetrics metrics;

    public XpRewardService(
            XpLancamentoRepository xpLancamentoRepository,
            JdbcTemplate jdbcTemplate,
            ApiObservabilityMetrics metrics
    ) {
        this.xpLancamentoRepository = xpLancamentoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    @Transactional
    public GrantResult grantAutomaticCompletionReward(UUID atividadeId, UUID eventoOcorrenciaId, Instant grantedAt) {
        if (atividadeId == null) throw new IllegalArgumentException("atividadeId is required");
        Instant effectiveGrantedAt = grantedAt == null ? Instant.now() : grantedAt;

        ActivityRewardData activity = loadActivityRewardData(atividadeId);
        if (activity.status() != AtividadeStatus.CONCLUIDA) {
            metrics.missionXpFailure("activity_not_completed");
            throw new ConflictException("XP reward requires completed activity id=" + atividadeId);
        }
        int value = activity.missaoValue();
        if (value < 0) {
            metrics.missionXpFailure("negative_mission_value");
            throw new ConflictException("Missao reward value must not be negative id=" + activity.missaoId());
        }
        if (value == 0) {
            return GrantResult.skippedZero(atividadeId);
        }

        ensureOccurrenceExists(eventoOcorrenciaId);
        String key = automaticCompletionKey(atividadeId);

        try {
            UUID insertedId = insertAutomaticGrant(activity, eventoOcorrenciaId, key, value, effectiveGrantedAt);
            if (insertedId != null) {
                XpLancamento inserted = xpLancamentoRepository.findById(insertedId)
                        .orElseThrow(() -> new NotFoundException("XpLancamento not found id=" + insertedId));
                metrics.missionXpGranted();
                metrics.missionXpAmount(value);
                return GrantResult.created(inserted);
            }
        } catch (DataIntegrityViolationException ex) {
            metrics.missionXpFailure("constraint_conflict");
            throw automaticGrantConflict(atividadeId, ex);
        }

        XpLancamento existing = xpLancamentoRepository.findByChaveIdempotencia(key)
                .orElseThrow(() -> new ConflictException("XP idempotency key conflict without readable ledger entry"));
        validateEquivalent(existing, activity, eventoOcorrenciaId, value);
        metrics.missionXpDuplicate();
        return GrantResult.duplicate(existing);
    }

    public static String automaticCompletionKey(UUID atividadeId) {
        if (atividadeId == null) throw new IllegalArgumentException("atividadeId is required");
        return "xp:activity:" + atividadeId + ":completion";
    }

    private UUID insertAutomaticGrant(
            ActivityRewardData activity,
            UUID eventoOcorrenciaId,
            String key,
            int value,
            Instant grantedAt
    ) {
        var rows = jdbcTemplate.query("""
                insert into xp_lancamento (
                    pessoa_id,
                    atividade_id,
                    missao_id,
                    evento_ocorrencia_id,
                    tipo,
                    quantidade,
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
                XpLancamentoTipo.CONCESSAO.name(),
                value,
                key,
                "Conclusao automatica da missao " + activity.missaoTitulo(),
                Timestamp.from(grantedAt),
                XpLancamento.AUTO_COMPLETION_CREATED_BY
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
                    m.value as missao_value
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
                        rs.getInt("missao_value")
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
        return xpLancamentoRepository.findFirstByAtividadeIdAndTipoAndCreatedBy(
                        atividadeId,
                        XpLancamentoTipo.CONCESSAO,
                        XpLancamento.AUTO_COMPLETION_CREATED_BY
                )
                .map(existing -> new ConflictException("Automatic completion XP already exists with divergent idempotency key for activity id=" + atividadeId))
                .orElse(new ConflictException("XP ledger constraint conflict for activity id=" + atividadeId));
    }

    private static void validateEquivalent(
            XpLancamento existing,
            ActivityRewardData activity,
            UUID eventoOcorrenciaId,
            int value
    ) {
        if (!Objects.equals(existing.getPessoa().getId(), activity.pessoaId())
                || !Objects.equals(existing.getAtividade().getId(), activity.atividadeId())
                || !Objects.equals(existing.getMissao().getId(), activity.missaoId())
                || !Objects.equals(eventId(existing), eventoOcorrenciaId)
                || existing.getQuantidade() != value
                || existing.getTipo() != XpLancamentoTipo.CONCESSAO) {
            throw new ConflictException("XP idempotency key reused with different reward content");
        }
    }

    private static UUID eventId(XpLancamento lancamento) {
        return lancamento.getEventoOcorrencia() == null ? null : lancamento.getEventoOcorrencia().getId();
    }

    private record ActivityRewardData(
            UUID atividadeId,
            String pessoaId,
            UUID missaoId,
            AtividadeStatus status,
            String missaoTitulo,
            int missaoValue
    ) {
    }

    public record GrantResult(
            boolean created,
            boolean duplicate,
            boolean skippedZeroValue,
            XpLancamento lancamento
    ) {
        static GrantResult created(XpLancamento lancamento) {
            return new GrantResult(true, false, false, lancamento);
        }

        static GrantResult duplicate(XpLancamento lancamento) {
            return new GrantResult(false, true, false, lancamento);
        }

        static GrantResult skippedZero(UUID atividadeId) {
            return new GrantResult(false, false, true, null);
        }
    }
}
