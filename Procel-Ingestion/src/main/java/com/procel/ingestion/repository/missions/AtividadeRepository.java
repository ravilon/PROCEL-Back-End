package com.procel.ingestion.repository.missions;

import com.procel.ingestion.entity.missions.Atividade;
import com.procel.ingestion.entity.missions.AtividadeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtividadeRepository extends JpaRepository<Atividade, UUID> {
    boolean existsByPessoaIdAndMissaoId(String pessoaId, UUID missaoId);
    List<Atividade> findByPessoaIdOrderByAssignedAtDesc(String pessoaId);
    List<Atividade> findByPessoaIdAndStatusOrderByAssignedAtDesc(String pessoaId, AtividadeStatus status);
    List<Atividade> findByMissaoIdAndStatusIn(UUID missaoId, List<AtividadeStatus> statuses);
    Optional<Atividade> findByIdAndPessoaId(UUID id, String pessoaId);
}
