package com.procel.api.service.missions;

import com.procel.api.entity.missions.Atividade;
import com.procel.api.entity.missions.AtividadeEventoTipo;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.Missao;
import com.procel.api.entity.people.Pessoa;
import com.procel.api.exception.ConflictException;
import com.procel.api.repository.missions.MissaoRepository;
import com.procel.api.repository.people.PessoaRepository;
import com.procel.api.service.academic.AcademicContext;
import com.procel.api.service.missions.beneficiaries.MissionBeneficiaryContext;
import com.procel.api.service.missions.beneficiaries.MissionBeneficiaryResolver;
import com.procel.api.service.missions.cycles.MissionCycleKeyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class MissionAssignedCycleProcessor {
    private final JdbcTemplate jdbc;
    private final MissaoRepository missions;
    private final PessoaRepository people;
    private final MissionBeneficiaryResolver beneficiaries;
    private final MissionCycleKeyFactory cycles;
    private final MissionActivityCycleService activities;
    private final MissionActivityProgressService progress;
    private final XpRewardService rewards;

    public MissionAssignedCycleProcessor(JdbcTemplate jdbc, MissaoRepository missions,
            PessoaRepository people, MissionBeneficiaryResolver beneficiaries,
            MissionCycleKeyFactory cycles, MissionActivityCycleService activities,
            MissionActivityProgressService progress, XpRewardService rewards) {
        this.jdbc = jdbc;
        this.missions = missions;
        this.people = people;
        this.beneficiaries = beneficiaries;
        this.cycles = cycles;
        this.activities = activities;
        this.progress = progress;
        this.rewards = rewards;
    }

    @Transactional
    public void assign(EventoOcorrencia occurrence, Optional<AcademicContext> academic,
            Optional<String> activator, Instant now, ZoneId zone) {
        Missao mission = occurrence.getEventoDefinicao().getMissao();
        UUID rootId = mission.getParent() == null ? mission.getId() : mission.getParent().getId();
        jdbc.queryForObject("select id from missao where id = ? for update",
                (rs, row) -> rs.getObject(1, UUID.class), rootId);
        List<UUID> existing = openAssignments(rootId, room(occurrence), classId(occurrence));
        if (!existing.isEmpty()) return;

        List<Missao> children = missions.findByParent_IdOrderByCreatedAtAsc(rootId).stream()
                .filter(missao -> missao != null && missao.isAtivo())
                .toList();
        if (mission.getParent() == null && missions.existsByParent_Id(rootId) && children.isEmpty()) {
            throw new ConflictException("Active parent mission requires an active child");
        }
        var resolution = beneficiaries.resolve(new MissionBeneficiaryContext(
                occurrence.getEventoDefinicao().getPoliticaAtribuicao(), occurrence,
                academic, activator, Optional.empty()));
        if (!resolution.supported()) throw new ConflictException("Unsupported beneficiary policy");
        for (String personId : new TreeSet<>(resolution.pessoaIds())) {
            Pessoa person = people.findById(personId)
                    .orElseThrow(() -> new ConflictException("Beneficiary not found: " + personId));
            Missao root = mission.getParent() == null ? mission : mission.getParent();
            var base = cycles.create(root, person, occurrence, occurrence.getDetectadoEm(), zone);
            Instant deadline = assignmentDeadline(occurrence, base.fim());
            String key = "assignment:" + occurrence.getId();
            Atividade parent = create(root, person, key, base.cicloTipo(), base.inicio(),
                    deadline, occurrence, null, now);
            for (Missao child : children) {
                create(child, person, key, base.cicloTipo(), base.inicio(), deadline,
                        occurrence, parent.getId(), now);
            }
        }
    }

    @Transactional
    public void progressAssigned(EventoOcorrencia occurrence, Instant now) {
        Missao mission = occurrence.getEventoDefinicao().getMissao();
        if (missions.existsByParent_Id(mission.getId())) {
            throw new ConflictException("Parent mission cannot progress from a sensor event");
        }
        for (AssignedActivity activity : matchingActivities(occurrence)) {
            var result = progress.applyProgress(activity.id(), occurrence.getId(), now);
            if (result.completed() && activity.parentId() != null) {
                completeParentIfReady(activity.parentId(), occurrence.getId(), now);
            }
        }
    }
    @Transactional
    public void expire(EventoOcorrencia occurrence, Instant now) {
        Missao mission = occurrence.getEventoDefinicao().getMissao();
        UUID rootId = mission.getParent() == null ? mission.getId() : mission.getParent().getId();
        jdbc.update("""
                update atividade
                   set status = 'EXPIRADA'
                 where status in ('PENDENTE', 'EM_ANDAMENTO')
                   and ciclo_fim is not null
                   and ciclo_fim <= ?
                   and compartimento_id = ?
                   and (periodo_aula_id = ? or ? is null)
                   and (missao_id = ? or missao_id in (select id from missao where parent_id = ?))
                """, Timestamp.from(now), room(occurrence), classId(occurrence), classId(occurrence), rootId, rootId);
    }

    @Transactional
    public void complete(EventoOcorrencia occurrence, Instant now) {
        Missao mission = occurrence.getEventoDefinicao().getMissao();
        if (missions.existsByParent_Id(mission.getId())) {
            throw new ConflictException("Parent mission cannot complete from a sensor event");
        }
        for (AssignedActivity activity : matchingActivities(occurrence)) {
            if (activity.parentId() != null) {
                lock(activity.parentId());
            }
            if (!completeActivity(activity.id(), occurrence.getId(), now)) continue;
            if (activity.parentId() != null) {
                completeParentIfReady(activity.parentId(), occurrence.getId(), now);
            }
        }
    }

    private static Instant assignmentDeadline(EventoOcorrencia occurrence, Instant fallback) {
        Integer seconds = occurrence.getEventoDefinicao().getJanelaSegundos();
        if (seconds == null || seconds <= 0) return fallback;
        return occurrence.getDetectadoEm().plus(Duration.ofSeconds(seconds));
    }

    private Atividade create(Missao mission, Pessoa person, String key,
            com.procel.api.entity.missions.MissaoCicloTipo cycleType, Instant start,
            Instant end, EventoOcorrencia origin, UUID parentId, Instant now) {
        var created = activities.localizarOuCriarWithResult(
                new MissionActivityCycleService.CreateCycleActivityCommand(
                        person.getId(), mission.getId(), key, cycleType, start, end));
        Atividade activity = created.atividade();
        if (created.created()) {
            jdbc.update("""
                    update atividade
                    set compartimento_id = ?, periodo_aula_id = ?,
                        atribuicao_ocorrencia_id = ?, parent_atividade_id = ?
                    where id = ?
                    """, room(origin), classId(origin), origin.getId(), parentId, activity.getId());
        }
        activities.registrarEventoIfAbsent(new MissionActivityCycleService.RegisterActivityEventCommand(
                activity.getId(), origin.getId(), AtividadeEventoTipo.INICIO, 0, now));
        return activity;
    }

    private List<UUID> openAssignments(UUID missionId, String room, UUID classId) {
        return jdbc.query("""
                select a.id from atividade a
                where a.missao_id = ? and a.parent_atividade_id is null
                  and a.compartimento_id = ?
                  and a.periodo_aula_id is not distinct from ?
                  and a.atribuicao_ocorrencia_id is not null
                  and a.status in ('PENDENTE', 'EM_ANDAMENTO')
                order by a.id
                """, (rs, row) -> rs.getObject(1, UUID.class), missionId, room, classId);
    }

    private List<AssignedActivity> matchingActivities(EventoOcorrencia occurrence) {
        UUID classId = classId(occurrence);
        String room = room(occurrence);
        List<AssignedActivity> rows = jdbc.query("""
                select a.id, a.parent_atividade_id, a.periodo_aula_id
                from atividade a
                join evento_ocorrencia origin on origin.id = a.atribuicao_ocorrencia_id
                where a.missao_id = ? and a.compartimento_id = ?
                  and a.atribuicao_ocorrencia_id is not null
                  and a.status in ('PENDENTE', 'EM_ANDAMENTO')
                  and origin.detectado_em <= ?
                  and (?::uuid is null or a.periodo_aula_id = ?::uuid)
                order by a.id
                """, (rs, row) -> new AssignedActivity(
                        rs.getObject("id", UUID.class),
                        rs.getObject("parent_atividade_id", UUID.class),
                        rs.getObject("periodo_aula_id", UUID.class)),
                occurrence.getEventoDefinicao().getMissao().getId(), room,
                Timestamp.from(occurrence.getDetectadoEm()), classId, classId);
        if (classId == null && rows.stream()
                .map(activity -> activity.classId())
                .filter(Objects::nonNull)
                .distinct()
                .count() > 1) {
            throw new ConflictException("Ambiguous class context for assigned mission");
        }
        return rows;
    }

    private boolean completeActivity(UUID id, UUID occurrenceId, Instant now) {
        lock(id);
        int changed = jdbc.update("""
                update atividade set status = 'CONCLUIDA',
                    completed_at = ?, ultimo_evento_em = ?,
                    progresso_atual = progresso_necessario
                where id = ? and status in ('PENDENTE', 'EM_ANDAMENTO')
                """, Timestamp.from(now), Timestamp.from(now), id);
        if (changed == 0) return false;
        activities.registrarEventoIfAbsent(new MissionActivityCycleService.RegisterActivityEventCommand(
                id, occurrenceId, AtividadeEventoTipo.CONCLUSAO, 0, now));
        rewards.grantAutomaticCompletionReward(id, occurrenceId, now);
        return true;
    }

    private void completeParentIfReady(UUID parentId, UUID occurrenceId, Instant now) {
        lock(parentId);
        Integer pending = jdbc.queryForObject("""
                select count(*) from atividade
                where parent_atividade_id = ? and status <> 'CONCLUIDA'
                """, Integer.class, parentId);
        Integer total = jdbc.queryForObject("""
                select count(*) from atividade where parent_atividade_id = ?
                """, Integer.class, parentId);
        if (Objects.equals(pending, 0) && total != null && total > 0) {
            completeActivity(parentId, occurrenceId, now);
        }
    }

    private void lock(UUID id) {
        jdbc.queryForObject("select id from atividade where id = ? for update",
                (rs, row) -> rs.getObject(1, UUID.class), id);
    }

    private static String room(EventoOcorrencia occurrence) {
        return occurrence.getCompartimento().getId();
    }

    private static UUID classId(EventoOcorrencia occurrence) {
        return occurrence.getPeriodoAula() == null ? null : occurrence.getPeriodoAula().getId();
    }

    private record AssignedActivity(UUID id, UUID parentId, UUID classId) {}
}
