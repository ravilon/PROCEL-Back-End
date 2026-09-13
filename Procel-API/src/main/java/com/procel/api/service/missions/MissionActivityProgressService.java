package com.procel.api.service.missions;

import com.procel.api.entity.missions.Atividade;
import com.procel.api.entity.missions.AtividadeEventoTipo;
import com.procel.api.entity.missions.AtividadeStatus;
import com.procel.api.exception.NotFoundException;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.repository.missions.AtividadeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class MissionActivityProgressService {

    private final AtividadeRepository atividadeRepository;
    private final MissionActivityCycleService cycleService;
    private final JdbcTemplate jdbcTemplate;
    private final ApiObservabilityMetrics metrics;

    public MissionActivityProgressService(
            AtividadeRepository atividadeRepository,
            MissionActivityCycleService cycleService,
            JdbcTemplate jdbcTemplate,
            ApiObservabilityMetrics metrics
    ) {
        this.atividadeRepository = atividadeRepository;
        this.cycleService = cycleService;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    @Transactional
    public ProgressOutcome applyProgress(UUID atividadeId, UUID eventoOcorrenciaId, Instant processadoEm) {
        if (atividadeId == null) throw new IllegalArgumentException("atividadeId is required");
        if (eventoOcorrenciaId == null) throw new IllegalArgumentException("eventoOcorrenciaId is required");
        Instant effectiveProcessedAt = processadoEm == null ? Instant.now() : processadoEm;

        LockedActivity locked = lockActivity(atividadeId);
        if (existingEvent(atividadeId, eventoOcorrenciaId, AtividadeEventoTipo.PROGRESSO)) {
            return new ProgressOutcome(false, false, true);
        }
        if (locked.terminal()) {
            return new ProgressOutcome(false, false, false);
        }

        var progressEvent = cycleService.registrarEventoIfAbsent(new MissionActivityCycleService.RegisterActivityEventCommand(
                atividadeId,
                eventoOcorrenciaId,
                AtividadeEventoTipo.PROGRESSO,
                1,
                effectiveProcessedAt
        ));
        if (!progressEvent.created()) {
            return new ProgressOutcome(false, false, true);
        }

        UpdatedActivity updated = incrementProgress(atividadeId, effectiveProcessedAt);
        if (updated == null) {
            return new ProgressOutcome(false, false, false);
        }
        metrics.missionActivityProgressed();

        boolean completed = false;
        if (updated.conclusaoAutomatica()
                && updated.progressoAtual() >= updated.progressoNecessario()
                && updated.status() != AtividadeStatus.CONCLUIDA) {
            completed = completeAutomatically(atividadeId, eventoOcorrenciaId, effectiveProcessedAt);
        }

        return new ProgressOutcome(true, completed, false);
    }

    private UpdatedActivity incrementProgress(UUID atividadeId, Instant processadoEm) {
        var rows = jdbcTemplate.query("""
                update atividade
                set progresso_atual = least(progresso_necessario, progresso_atual + 1),
                    status = case when status = 'PENDENTE' then 'EM_ANDAMENTO' else status end,
                    started_at = coalesce(started_at, ?),
                    ultimo_evento_em = ?,
                    completed_at = case when status = 'PENDENTE' then null else completed_at end
                where id = ?
                  and status in ('PENDENTE', 'EM_ANDAMENTO')
                  and progresso_atual < progresso_necessario
                returning progresso_atual, progresso_necessario, conclusao_automatica, status
                """,
                (rs, rowNum) -> new UpdatedActivity(
                        rs.getInt("progresso_atual"),
                        rs.getInt("progresso_necessario"),
                        rs.getBoolean("conclusao_automatica"),
                        AtividadeStatus.valueOf(rs.getString("status"))
                ),
                Timestamp.from(processadoEm),
                Timestamp.from(processadoEm),
                atividadeId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean completeAutomatically(UUID atividadeId, UUID eventoOcorrenciaId, Instant processadoEm) {
        var completionEvent = cycleService.registrarEventoIfAbsent(new MissionActivityCycleService.RegisterActivityEventCommand(
                atividadeId,
                eventoOcorrenciaId,
                AtividadeEventoTipo.CONCLUSAO,
                0,
                processadoEm
        ));
        if (!completionEvent.created()) {
            return false;
        }

        int updated = jdbcTemplate.update("""
                update atividade
                set status = 'CONCLUIDA',
                    completed_at = coalesce(completed_at, ?),
                    ultimo_evento_em = ?
                where id = ?
                  and status = 'EM_ANDAMENTO'
                  and conclusao_automatica = true
                  and progresso_atual >= progresso_necessario
                """,
                Timestamp.from(processadoEm),
                Timestamp.from(processadoEm),
                atividadeId
        );
        if (updated > 0) {
            metrics.missionActivityCompleted();
            return true;
        }
        return false;
    }

    private LockedActivity lockActivity(UUID atividadeId) {
        var rows = jdbcTemplate.query("""
                select status
                from atividade
                where id = ?
                for update
                """,
                (rs, rowNum) -> new LockedActivity(AtividadeStatus.valueOf(rs.getString("status"))),
                atividadeId
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("Atividade not found id=" + atividadeId);
        }
        return rows.getFirst();
    }

    private boolean existingEvent(UUID atividadeId, UUID eventoOcorrenciaId, AtividadeEventoTipo tipo) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from atividade_evento
                where atividade_id = ?
                  and evento_ocorrencia_id = ?
                  and tipo = ?
                """, Integer.class, atividadeId, eventoOcorrenciaId, tipo.name());
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public Atividade buscarAtividade(UUID atividadeId) {
        return atividadeRepository.findById(atividadeId)
                .orElseThrow(() -> new NotFoundException("Atividade not found id=" + atividadeId));
    }

    public record ProgressOutcome(
            boolean progressed,
            boolean completed,
            boolean duplicate
    ) {
    }

    private record LockedActivity(AtividadeStatus status) {
        boolean terminal() {
            return status == AtividadeStatus.CONCLUIDA
                    || status == AtividadeStatus.EXPIRADA
                    || status == AtividadeStatus.CANCELADA;
        }
    }

    private record UpdatedActivity(
            int progressoAtual,
            int progressoNecessario,
            boolean conclusaoAutomatica,
            AtividadeStatus status
    ) {
    }
}
