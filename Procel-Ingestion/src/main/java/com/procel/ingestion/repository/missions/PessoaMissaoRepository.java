package com.procel.ingestion.repository.missions;

import com.procel.ingestion.entity.missions.AtividadeStatus;
import com.procel.ingestion.entity.missions.PessoaMissao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PessoaMissaoRepository extends JpaRepository<PessoaMissao, UUID> {
    boolean existsByPessoaIdAndMissaoId(String pessoaId, UUID missaoId);
    List<PessoaMissao> findByPessoaIdOrderByAssignedAtDesc(String pessoaId);
    List<PessoaMissao> findByPessoaIdAndStatusOrderByAssignedAtDesc(String pessoaId, AtividadeStatus status);
    Optional<PessoaMissao> findByIdAndPessoaId(UUID id, String pessoaId);
}
