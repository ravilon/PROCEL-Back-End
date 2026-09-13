package com.procel.api.repository.people;

import com.procel.api.entity.people.AlunoDisciplina;
import com.procel.api.entity.people.AlunoDisciplinaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select ad
            from AlunoDisciplina ad
            join fetch ad.pessoa pessoa
            join fetch ad.disciplina disciplina
            where disciplina.id = :disciplinaId
              and ad.turma = :turma
              and ad.status = :status
            order by pessoa.nome asc
            """)
    List<AlunoDisciplina> findEligibleAcademicContext(
            @Param("disciplinaId") Long disciplinaId,
            @Param("turma") String turma,
            @Param("status") AlunoDisciplinaStatus status
    );

    Optional<AlunoDisciplina> findByIdAndPessoaId(UUID id, String pessoaId);
}
