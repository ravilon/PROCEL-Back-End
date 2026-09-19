package com.procel.api.service.missions;

import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.entity.missions.Atividade;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoPapel;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.entity.missions.Missao;
import com.procel.api.entity.missions.MissaoCicloTipo;
import com.procel.api.entity.people.Pessoa;
import com.procel.api.exception.NotFoundException;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.repository.missions.EventoOcorrenciaRepository;
import com.procel.api.repository.missions.MissaoRepository;
import com.procel.api.repository.people.PessoaRepository;
import com.procel.api.service.academic.AcademicContext;
import com.procel.api.service.missions.beneficiaries.MissionBeneficiaryContext;
import com.procel.api.service.missions.beneficiaries.MissionBeneficiaryResolver;
import com.procel.api.service.missions.cycles.MissionCycleKeyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class MissionEventActivityProcessor {

    private final EventoOcorrenciaRepository ocorrenciaRepository;
    private final MissaoRepository missaoRepository;
    private final PessoaRepository pessoaRepository;
    private final MissionBeneficiaryResolver beneficiaryResolver;
    private final MissionCycleKeyFactory cycleKeyFactory;
    private final MissionActivityCycleService cycleService;
    private final MissionActivityProgressService progressService;
    private final EventoOcorrenciaService ocorrenciaService;
    private final MissionEvaluationProperties properties;
    private final ApiObservabilityMetrics metrics;
    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private MissionAssignedCycleProcessor assignedCycles;

    public MissionEventActivityProcessor(
            EventoOcorrenciaRepository ocorrenciaRepository,
            MissaoRepository missaoRepository,
            PessoaRepository pessoaRepository,
            MissionBeneficiaryResolver beneficiaryResolver,
            MissionCycleKeyFactory cycleKeyFactory,
            MissionActivityCycleService cycleService,
            MissionActivityProgressService progressService,
            EventoOcorrenciaService ocorrenciaService,
            MissionEvaluationProperties properties,
            ApiObservabilityMetrics metrics,
            JdbcTemplate jdbcTemplate
    ) {
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.missaoRepository = missaoRepository;
        this.pessoaRepository = pessoaRepository;
        this.beneficiaryResolver = beneficiaryResolver;
        this.cycleKeyFactory = cycleKeyFactory;
        this.cycleService = cycleService;
        this.progressService = progressService;
        this.ocorrenciaService = ocorrenciaService;
        this.properties = properties;
        this.metrics = metrics;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void process(
            UUID ocorrenciaId,
            Optional<AcademicContext> academicContext,
            Optional<String> pessoaAtivadoraId,
            Instant processedAt
    ) {
        try {
            EventoOcorrenciaStatus lockedStatus = lockOccurrence(ocorrenciaId);
            if (lockedStatus == EventoOcorrenciaStatus.PROCESSADO) {
                return;
            }
            if (lockedStatus != EventoOcorrenciaStatus.CONFIRMADO) {
                throw permanent("Only CONFIRMADO occurrences can be processed id=" + ocorrenciaId);
            }

            EventoOcorrencia occurrence = ocorrenciaRepository.findById(ocorrenciaId)
                    .orElseThrow(() -> new NotFoundException("EventoOcorrencia not found id=" + ocorrenciaId));
            var event = occurrence.getEventoDefinicao();
            Missao missao = event.getMissao();
            if (event.getPapel() == EventoPapel.ATRIBUICAO) {
                assignedCycles.assign(occurrence, academicContext, pessoaAtivadoraId, processedAt, properties.getAcademicZone());
                ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.PROCESSADO);
                return;
            }
            if (event.getPapel() == EventoPapel.CONCLUSAO) {
                assignedCycles.complete(occurrence, processedAt);
                ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.PROCESSADO);
                return;
            }
            if (missao.getParent() != null) {
                assignedCycles.progressAssigned(occurrence, processedAt);
                ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.PROCESSADO);
                return;
            }
            if (missaoRepository.existsByParent_Id(missao.getId())) throw permanent("Parent mission cannot progress from a sensor event");
            validateHierarchy(missao);

            var resolution = beneficiaryResolver.resolve(new MissionBeneficiaryContext(
                    event.getPoliticaAtribuicao(),
                    occurrence,
                    academicContext,
                    pessoaAtivadoraId,
                    Optional.empty()
            ));
            if (!resolution.supported()) {
                throw permanent("Attribution policy not supported: " + event.getPoliticaAtribuicao());
            }

            TreeSet<String> beneficiaries = new TreeSet<>(resolution.pessoaIds());
            metrics.missionBeneficiariesResolved(beneficiaries.size());
            if (beneficiaries.isEmpty()) {
                metrics.missionBeneficiariesEmpty();
            }

            for (String pessoaId : beneficiaries) {
                Pessoa pessoa = pessoaRepository.findById(pessoaId)
                        .orElseThrow(() -> new NotFoundException("Pessoa not found id=" + pessoaId));
                var cycleKey = cycleKeyFactory.create(
                        missao,
                        pessoa,
                        occurrence,
                        occurrence.getDetectadoEm(),
                        properties.getAcademicZone()
                );
                var activityResult = cycleService.localizarOuCriarWithResult(new MissionActivityCycleService.CreateCycleActivityCommand(
                        pessoa.getId(),
                        missao.getId(),
                        cycleKey.chave(),
                        cycleKey.cicloTipo(),
                        cycleKey.inicio(),
                        cycleKey.fim()
                ));
                if (activityResult.created()) {
                    metrics.missionActivityCreated();
                }
                Atividade atividade = activityResult.atividade();
                progressService.applyProgress(atividade.getId(), occurrence.getId(), processedAt);
            }

            ocorrenciaService.atualizarStatus(occurrence.getId(), EventoOcorrenciaStatus.PROCESSADO);
        } catch (MissionEventActivityProcessingException ex) {
            metrics.missionActivityProcessingFailure(ex.permanent() ? "permanent" : "transient");
            throw ex;
        } catch (UnsupportedOperationException | IllegalArgumentException | NotFoundException ex) {
            metrics.missionActivityProcessingFailure("permanent");
            throw new MissionEventActivityProcessingException(rootMessage(ex), true, ex);
        } catch (DataAccessException ex) {
            metrics.missionActivityProcessingFailure("transient");
            throw new MissionEventActivityProcessingException(rootMessage(ex), false, ex);
        }
    }

    private void validateHierarchy(Missao missao) {
        MissaoCicloTipo cicloTipo = missao.getCicloTipo() == null ? MissaoCicloTipo.UNICA : missao.getCicloTipo();
        boolean hasHierarchy = missao.getParent() != null || missaoRepository.existsByParent_Id(missao.getId());
        if (hasHierarchy && cicloTipo != MissaoCicloTipo.UNICA) {
            throw permanent("Recurring mission hierarchy is not supported for missaoId=" + missao.getId());
        }
    }

    private EventoOcorrenciaStatus lockOccurrence(UUID ocorrenciaId) {
        var rows = jdbcTemplate.query("""
                select status
                from evento_ocorrencia
                where id = ?
                for update
                """,
                (rs, rowNum) -> EventoOcorrenciaStatus.valueOf(rs.getString("status")),
                ocorrenciaId
        );
        if (rows.isEmpty()) {
            throw new NotFoundException("EventoOcorrencia not found id=" + ocorrenciaId);
        }
        return rows.getFirst();
    }

    private MissionEventActivityProcessingException permanent(String message) {
        return new MissionEventActivityProcessingException(message, true);
    }

    private String rootMessage(Throwable throwable) {
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
}
