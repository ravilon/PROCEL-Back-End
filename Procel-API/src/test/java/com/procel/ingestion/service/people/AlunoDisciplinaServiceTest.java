package com.procel.ingestion.service.people;

import com.procel.ingestion.dto.people.AlunoDisciplinaDTOs;
import com.procel.ingestion.entity.people.AlunoDisciplina;
import com.procel.ingestion.entity.people.AlunoDisciplinaStatus;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.entity.rooms.Disciplina;
import com.procel.ingestion.exception.ConflictException;
import com.procel.ingestion.repository.people.AlunoDisciplinaRepository;
import com.procel.ingestion.repository.people.PessoaRepository;
import com.procel.ingestion.repository.rooms.DisciplinaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlunoDisciplinaServiceTest {

    private final AlunoDisciplinaRepository alunoDisciplinaRepo =
            mock(AlunoDisciplinaRepository.class);
    private final PessoaRepository pessoaRepo = mock(PessoaRepository.class);
    private final DisciplinaRepository disciplinaRepo =
            mock(DisciplinaRepository.class);
    private final AlunoDisciplinaService service = new AlunoDisciplinaService(
            alunoDisciplinaRepo,
            pessoaRepo,
            disciplinaRepo
    );

    @Test
    void linksStudentToDiscipline() {
        Pessoa pessoa = pessoa();
        Disciplina disciplina = disciplina();
        var request = new AlunoDisciplinaDTOs.VincularDisciplinaRequest(
                27064L,
                " T1 ",
                "2026/1",
                null
        );

        when(pessoaRepo.findById("aluno1")).thenReturn(Optional.of(pessoa));
        when(disciplinaRepo.findById(27064L)).thenReturn(Optional.of(disciplina));
        when(alunoDisciplinaRepo.save(any(AlunoDisciplina.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.vincular(" aluno1 ", request);

        assertThat(response.pessoaId()).isEqualTo("aluno1");
        assertThat(response.disciplinaId()).isEqualTo(27064L);
        assertThat(response.disciplinaNome()).isEqualTo("SISTEMAS DISCRETOS");
        assertThat(response.turma()).isEqualTo("T1");
        assertThat(response.periodoLetivo()).isEqualTo("2026/1");
        assertThat(response.status()).isEqualTo(AlunoDisciplinaStatus.ATIVA);
        verify(alunoDisciplinaRepo).save(any(AlunoDisciplina.class));
    }

    @Test
    void rejectsDuplicateLink() {
        Pessoa pessoa = pessoa();
        Disciplina disciplina = disciplina();
        var request = new AlunoDisciplinaDTOs.VincularDisciplinaRequest(
                27064L,
                "T1",
                "2026/1",
                AlunoDisciplinaStatus.ATIVA
        );

        when(pessoaRepo.findById("aluno1")).thenReturn(Optional.of(pessoa));
        when(disciplinaRepo.findById(27064L)).thenReturn(Optional.of(disciplina));
        when(alunoDisciplinaRepo
                .existsByPessoaIdAndDisciplinaIdAndTurmaAndPeriodoLetivo(
                        "aluno1",
                        27064L,
                        "T1",
                        "2026/1"
                ))
                .thenReturn(true);

        assertThatThrownBy(() -> service.vincular("aluno1", request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listsDisciplinesForRequestedAcademicPeriod() {
        AlunoDisciplina vinculo = new AlunoDisciplina(
                pessoa(),
                disciplina(),
                "T1",
                "2026/1",
                AlunoDisciplinaStatus.ATIVA
        );

        when(pessoaRepo.existsById("aluno1")).thenReturn(true);
        when(alunoDisciplinaRepo
                .findByPessoaIdAndPeriodoLetivoOrderByDisciplinaNomeAscTurmaAsc(
                        "aluno1",
                        "2026/1"
                ))
                .thenReturn(List.of(vinculo));

        var response = service.listarPorPeriodo("aluno1", "2026/1");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().disciplinaId()).isEqualTo(27064L);
        assertThat(response.getFirst().periodoLetivo()).isEqualTo("2026/1");
    }

    @Test
    void rejectsInvalidAcademicPeriod() {
        assertThatThrownBy(() -> service.listarPorPeriodo("aluno1", "2026-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAAA/S");
    }

    private Pessoa pessoa() {
        return new Pessoa(
                "aluno1",
                "Aluno Exemplo",
                "aluno@example.com",
                "hash",
                null,
                "MAT-1"
        );
    }

    private Disciplina disciplina() {
        return new Disciplina(27064L, "SISTEMAS DISCRETOS", "CDTEC");
    }
}

