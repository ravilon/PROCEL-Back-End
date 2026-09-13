package com.procel.api.service.academic;

import com.procel.api.entity.people.AlunoDisciplina;
import com.procel.api.entity.people.AlunoDisciplinaStatus;
import com.procel.api.entity.people.Pessoa;
import com.procel.api.entity.rooms.Campus;
import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.rooms.Disciplina;
import com.procel.api.entity.rooms.PeriodoAula;
import com.procel.api.entity.rooms.PeriodoAulaTipo;
import com.procel.api.entity.rooms.Predio;
import com.procel.api.entity.rooms.Unidade;
import com.procel.api.exception.ConflictException;
import com.procel.api.repository.people.AlunoDisciplinaRepository;
import com.procel.api.repository.rooms.PeriodoAulaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AcademicContextResolverTest {

    private final PeriodoAulaRepository periodoAulaRepository =
            mock(PeriodoAulaRepository.class);
    private final AlunoDisciplinaRepository alunoDisciplinaRepository =
            mock(AlunoDisciplinaRepository.class);
    private final AcademicContextResolver resolver = new AcademicContextResolver(
            periodoAulaRepository,
            alunoDisciplinaRepository
    );

    @Test
    void findsClassByRoomAndTime() {
        PeriodoAula periodoAula = periodoAula(disciplina(27064L), "T1");
        whenClassLookupReturns(LocalTime.of(8, 10), List.of(periodoAula));
        whenEligibleLookupReturns(27064L, "T1", List.of());

        AcademicContext context = resolver.resolve(
                " ROOM-1 ",
                Instant.parse("2026-06-16T11:10:00Z")
        );

        assertThat(context.empty()).isFalse();
        assertThat(context.compartimentoId()).isEqualTo("ROOM-1");
        assertThat(context.disciplinaId()).isEqualTo(27064L);
        assertThat(context.turma()).isEqualTo("T1");
        assertThat(context.inicio()).isEqualTo(LocalDateTime.of(2026, 6, 16, 8, 0));
        assertThat(context.fim()).isEqualTo(LocalDateTime.of(2026, 6, 16, 8, 50));
    }

    @Test
    void findsStudentsFromSameDisciplineAndClassGroup() {
        Disciplina disciplina = disciplina(27064L);
        PeriodoAula periodoAula = periodoAula(disciplina, "T1");
        AlunoDisciplina aluno1 = vinculo("aluno1", disciplina, "T1", "2026/1", AlunoDisciplinaStatus.ATIVA);
        AlunoDisciplina aluno2 = vinculo("aluno2", disciplina, "T1", "2026/1", AlunoDisciplinaStatus.ATIVA);

        whenClassLookupReturns(LocalTime.of(8, 10), List.of(periodoAula));
        whenEligibleLookupReturns(27064L, "T1", List.of(aluno1, aluno2));

        AcademicContext context = resolver.resolve("ROOM-1", Instant.parse("2026-06-16T11:10:00Z"));

        assertThat(context.pessoaElegivelIds()).containsExactly("aluno1", "aluno2");
    }

    @Test
    void doesNotIncludeStudentFromAnotherClassGroup() {
        Disciplina disciplina = disciplina(27064L);
        PeriodoAula periodoAula = periodoAula(disciplina, "T1");
        AlunoDisciplina mesmaTurma = vinculo("aluno1", disciplina, "T1", "2026/1", AlunoDisciplinaStatus.ATIVA);
        AlunoDisciplina outraTurma = vinculo("aluno2", disciplina, "T2", "2026/1", AlunoDisciplinaStatus.ATIVA);

        whenClassLookupReturns(LocalTime.of(8, 10), List.of(periodoAula));
        whenEligibleLookupReturns(27064L, "T1", List.of(mesmaTurma, outraTurma));

        AcademicContext context = resolver.resolve("ROOM-1", Instant.parse("2026-06-16T11:10:00Z"));

        assertThat(context.pessoaElegivelIds()).containsExactly("aluno1");
    }

    @Test
    void doesNotIncludeStudentFromAnotherDiscipline() {
        Disciplina disciplina = disciplina(27064L);
        Disciplina outraDisciplina = disciplina(99999L);
        PeriodoAula periodoAula = periodoAula(disciplina, "T1");
        AlunoDisciplina mesmaDisciplina = vinculo("aluno1", disciplina, "T1", "2026/1", AlunoDisciplinaStatus.ATIVA);
        AlunoDisciplina outra = vinculo("aluno2", outraDisciplina, "T1", "2026/1", AlunoDisciplinaStatus.ATIVA);

        whenClassLookupReturns(LocalTime.of(8, 10), List.of(periodoAula));
        whenEligibleLookupReturns(27064L, "T1", List.of(mesmaDisciplina, outra));

        AcademicContext context = resolver.resolve("ROOM-1", Instant.parse("2026-06-16T11:10:00Z"));

        assertThat(context.pessoaElegivelIds()).containsExactly("aluno1");
    }

    @Test
    void doesNotIncludeCanceledLink() {
        Disciplina disciplina = disciplina(27064L);
        PeriodoAula periodoAula = periodoAula(disciplina, "T1");
        AlunoDisciplina ativa = vinculo("aluno1", disciplina, "T1", "2026/1", AlunoDisciplinaStatus.ATIVA);
        AlunoDisciplina cancelada = vinculo("aluno2", disciplina, "T1", "2026/1", AlunoDisciplinaStatus.CANCELADA);

        whenClassLookupReturns(LocalTime.of(8, 10), List.of(periodoAula));
        whenEligibleLookupReturns(27064L, "T1", List.of(ativa, cancelada));

        AcademicContext context = resolver.resolve("ROOM-1", Instant.parse("2026-06-16T11:10:00Z"));

        assertThat(context.pessoaElegivelIds()).containsExactly("aluno1");
        verify(alunoDisciplinaRepository).findEligibleAcademicContext(
                27064L,
                "T1",
                AlunoDisciplinaStatus.ATIVA
        );
    }

    @Test
    void returnsEmptyContextWhenThereIsNoClassAtTime() {
        whenClassLookupReturns(LocalTime.of(12, 0), List.of());

        AcademicContext context = resolver.resolve(
                "ROOM-1",
                Instant.parse("2026-06-16T15:00:00Z")
        );

        assertThat(context.empty()).isTrue();
        assertThat(context.compartimentoId()).isEqualTo("ROOM-1");
        assertThat(context.pessoaElegivelIds()).isEmpty();
        verifyNoInteractions(alunoDisciplinaRepository);
    }

    @Test
    void rejectsOverlappingClassesAsAmbiguous() {
        PeriodoAula first = periodoAula(disciplina(27064L), "T1");
        PeriodoAula second = new PeriodoAula(
                first.getCompartimento(),
                first.getDisciplina(),
                LocalDate.of(2026, 6, 16),
                1,
                2,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                "T1",
                PeriodoAulaTipo.AULA,
                "Sobreposta",
                "TEST"
        );
        whenClassLookupReturns(LocalTime.of(8, 10), List.of(first, second));

        assertThatThrownBy(() -> resolver.resolve(
                "ROOM-1",
                Instant.parse("2026-06-16T11:10:00Z")
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ambiguous academic context");

        verifyNoInteractions(alunoDisciplinaRepository);
    }

    @Test
    void preservesAcademicPeriodFromAlunoDisciplina() {
        Disciplina disciplina = disciplina(27064L);
        PeriodoAula periodoAula = periodoAula(disciplina, "T1");
        AlunoDisciplina vinculo = vinculo("aluno1", disciplina, "T1", "2026/1", AlunoDisciplinaStatus.ATIVA);

        whenClassLookupReturns(LocalTime.of(8, 10), List.of(periodoAula));
        whenEligibleLookupReturns(27064L, "T1", List.of(vinculo));

        AcademicContext context = resolver.resolve("ROOM-1", Instant.parse("2026-06-16T11:10:00Z"));

        assertThat(context.periodoLetivo()).isEqualTo("2026/1");
    }

    @Test
    void rejectsMultipleActiveAcademicPeriodsForSameClassGroup() {
        Disciplina disciplina = disciplina(27064L);
        PeriodoAula periodoAula = periodoAula(disciplina, "T1");
        AlunoDisciplina first = vinculo("aluno1", disciplina, "T1", "2026/1", AlunoDisciplinaStatus.ATIVA);
        AlunoDisciplina second = vinculo("aluno2", disciplina, "T1", "2026/2", AlunoDisciplinaStatus.ATIVA);

        whenClassLookupReturns(LocalTime.of(8, 10), List.of(periodoAula));
        whenEligibleLookupReturns(27064L, "T1", List.of(first, second));

        assertThatThrownBy(() -> resolver.resolve(
                "ROOM-1",
                Instant.parse("2026-06-16T11:10:00Z")
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ambiguous academic period");
    }

    private void whenClassLookupReturns(LocalTime hora, List<PeriodoAula> periodosAula) {
        when(periodoAulaRepository
                .findByCompartimentoIdAndDataAndHoraInicioLessThanEqualAndHoraFimGreaterThanOrderByTurnoAscPeriodoAulaAsc(
                        "ROOM-1",
                        LocalDate.of(2026, 6, 16),
                        hora,
                        hora
                ))
                .thenReturn(periodosAula);
    }

    private void whenEligibleLookupReturns(
            Long disciplinaId,
            String turma,
            List<AlunoDisciplina> vinculos
    ) {
        when(alunoDisciplinaRepository.findEligibleAcademicContext(
                disciplinaId,
                turma,
                AlunoDisciplinaStatus.ATIVA
        )).thenReturn(vinculos);
    }

    private PeriodoAula periodoAula(Disciplina disciplina, String turma) {
        Campus campus = new Campus("Campus");
        Unidade unidade = new Unidade("Unidade");
        Predio predio = new Predio(campus, "Predio");
        Compartimento compartimento = new Compartimento(
                "ROOM-1",
                predio,
                unidade,
                "Sala 1",
                "Sala de Aula"
        );
        return new PeriodoAula(
                compartimento,
                disciplina,
                LocalDate.of(2026, 6, 16),
                1,
                1,
                LocalTime.of(8, 0),
                LocalTime.of(8, 50),
                turma,
                PeriodoAulaTipo.AULA,
                "(CDTEC) " + turma + " - SISTEMAS DISCRETOS",
                "TEST"
        );
    }

    private Disciplina disciplina(Long id) {
        return new Disciplina(id, "SISTEMAS DISCRETOS", "CDTEC");
    }

    private AlunoDisciplina vinculo(
            String pessoaId,
            Disciplina disciplina,
            String turma,
            String periodoLetivo,
            AlunoDisciplinaStatus status
    ) {
        return new AlunoDisciplina(
                pessoa(pessoaId),
                disciplina,
                turma,
                periodoLetivo,
                status
        );
    }

    private Pessoa pessoa(String id) {
        return new Pessoa(
                id,
                "Aluno " + id,
                id + "@example.test",
                "hash",
                null,
                "MAT-" + id
        );
    }
}
