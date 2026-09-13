package com.procel.api.service.academic;

import com.procel.api.entity.people.AlunoDisciplina;
import com.procel.api.entity.people.AlunoDisciplinaStatus;
import com.procel.api.entity.rooms.Disciplina;
import com.procel.api.entity.rooms.PeriodoAula;
import com.procel.api.exception.ConflictException;
import com.procel.api.repository.people.AlunoDisciplinaRepository;
import com.procel.api.repository.rooms.PeriodoAulaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AcademicContextResolver {

    private static final ZoneId ACADEMIC_ZONE = ZoneId.of("America/Sao_Paulo");

    private final PeriodoAulaRepository periodoAulaRepository;
    private final AlunoDisciplinaRepository alunoDisciplinaRepository;

    public AcademicContextResolver(
            PeriodoAulaRepository periodoAulaRepository,
            AlunoDisciplinaRepository alunoDisciplinaRepository
    ) {
        this.periodoAulaRepository = periodoAulaRepository;
        this.alunoDisciplinaRepository = alunoDisciplinaRepository;
    }

    @Transactional(readOnly = true)
    public AcademicContext resolve(
            String compartimentoId,
            Instant instant
    ) {
        String normalizedCompartimentoId = normalizeRequired(compartimentoId, "compartimentoId");
        if (instant == null) {
            throw new IllegalArgumentException("instant is required");
        }

        LocalDate data = LocalDate.ofInstant(instant, ACADEMIC_ZONE);
        LocalTime hora = LocalTime.ofInstant(instant, ACADEMIC_ZONE);

        List<PeriodoAula> matches = periodoAulaRepository
                .findByCompartimentoIdAndDataAndHoraInicioLessThanEqualAndHoraFimGreaterThanOrderByTurnoAscPeriodoAulaAsc(
                        normalizedCompartimentoId,
                        data,
                        hora,
                        hora
                );

        if (matches.isEmpty()) {
            return AcademicContext.empty(normalizedCompartimentoId);
        }
        if (matches.size() > 1) {
            throw new ConflictException(
                    "Ambiguous academic context for compartimentoId=" +
                            normalizedCompartimentoId + ", instant=" + instant
            );
        }

        PeriodoAula periodoAula = matches.getFirst();
        List<AlunoDisciplina> elegiveis = eligibleStudents(periodoAula);
        String periodoLetivo = resolvePeriodoLetivo(periodoAula, elegiveis);
        Disciplina disciplina = periodoAula.getDisciplina();

        return new AcademicContext(
                periodoAula.getId(),
                disciplina == null ? null : disciplina.getId(),
                periodoAula.getTurma(),
                periodoLetivo,
                normalizedCompartimentoId,
                LocalDateTime.of(periodoAula.getData(), periodoAula.getHoraInicio()),
                LocalDateTime.of(periodoAula.getData(), periodoAula.getHoraFim()),
                elegiveis.stream()
                        .map(alunoDisciplina -> alunoDisciplina.getPessoa().getId())
                        .toList()
        );
    }

    private List<AlunoDisciplina> eligibleStudents(PeriodoAula periodoAula) {
        Disciplina disciplina = periodoAula.getDisciplina();
        if (disciplina == null
                || periodoAula.getTurma() == null
                || periodoAula.getTurma().isBlank()) {
            return List.of();
        }

        return alunoDisciplinaRepository.findEligibleAcademicContext(
                disciplina.getId(),
                periodoAula.getTurma(),
                AlunoDisciplinaStatus.ATIVA
        ).stream()
                .filter(alunoDisciplina -> alunoDisciplina.getStatus() == AlunoDisciplinaStatus.ATIVA)
                .filter(alunoDisciplina -> sameDiscipline(alunoDisciplina, disciplina))
                .filter(alunoDisciplina -> periodoAula.getTurma().equals(alunoDisciplina.getTurma()))
                .toList();
    }

    private static String resolvePeriodoLetivo(
            PeriodoAula periodoAula,
            List<AlunoDisciplina> elegiveis
    ) {
        Set<String> periodosLetivos = elegiveis.stream()
                .map(AlunoDisciplina::getPeriodoLetivo)
                .filter(periodoLetivo -> periodoLetivo != null && !periodoLetivo.isBlank())
                .collect(Collectors.toUnmodifiableSet());

        if (periodosLetivos.size() > 1) {
            Long disciplinaId = periodoAula.getDisciplina() == null
                    ? null
                    : periodoAula.getDisciplina().getId();
            throw new ConflictException(
                    "Ambiguous academic period for periodoAulaId=" + periodoAula.getId() +
                            ", disciplinaId=" + disciplinaId +
                            ", turma=" + periodoAula.getTurma()
            );
        }

        return periodosLetivos.stream().findFirst().orElse(null);
    }

    private static boolean sameDiscipline(
            AlunoDisciplina alunoDisciplina,
            Disciplina disciplina
    ) {
        return alunoDisciplina.getDisciplina() != null
                && disciplina.getId().equals(alunoDisciplina.getDisciplina().getId());
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
