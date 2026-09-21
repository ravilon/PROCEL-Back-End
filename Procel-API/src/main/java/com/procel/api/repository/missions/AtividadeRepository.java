package com.procel.api.repository.missions;

import com.procel.api.entity.missions.Atividade;
import com.procel.api.entity.missions.AtividadeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.procel.api.entity.missions.AtividadeStatus;
import java.util.Optional;
import java.util.UUID;

public interface AtividadeRepository extends JpaRepository<Atividade, UUID> {
    boolean existsByPessoaIdAndMissaoId(String pessoaId, UUID missaoId);
    boolean existsByMissaoId(UUID missaoId);
    boolean existsByPessoaIdAndMissaoIdAndChaveCiclo(String pessoaId, UUID missaoId, String chaveCiclo);
    List<Atividade> findByPessoaIdOrderByAssignedAtDesc(String pessoaId);

    List<Atividade> findByStatusInAndCicloFimLessThanEqual(List<AtividadeStatus> statuses, java.time.Instant cicloFim);
    List<Atividade> findByPessoaIdAndStatusOrderByAssignedAtDesc(String pessoaId, AtividadeStatus status);
    List<Atividade> findByMissaoIdAndStatusIn(UUID missaoId, List<AtividadeStatus> statuses);
    Optional<Atividade> findByIdAndPessoaId(UUID id, String pessoaId);
    Optional<Atividade> findByPessoaIdAndMissaoId(String pessoaId, UUID missaoId);
    Optional<Atividade> findByPessoaIdAndMissaoIdAndChaveCiclo(String pessoaId, UUID missaoId, String chaveCiclo);
}
