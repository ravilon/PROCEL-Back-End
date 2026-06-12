package com.procel.ingestion.repository.people;

import com.procel.ingestion.entity.people.AlunoDisciplina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlunoDisciplinaRepository extends JpaRepository<AlunoDisciplina, UUID> {

    boolean existsByPessoaIdAndDisciplinaIdAndTurmaAndPeriodoLetivo(
            String pessoaId,
            Long disciplinaId,
            String turma,
            String periodoLetivo
    );

    List<AlunoDisciplina> findByPessoaIdAndPeriodoLetivoOrderByDisciplinaNomeAscTurmaAsc(
            String pessoaId,
            String periodoLetivo
    );

    Optional<AlunoDisciplina> findByIdAndPessoaId(UUID id, String pessoaId);
}

