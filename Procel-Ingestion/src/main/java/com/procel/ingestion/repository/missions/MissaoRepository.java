package com.procel.ingestion.repository.missions;

import com.procel.ingestion.entity.missions.Missao;
import com.procel.ingestion.entity.missions.MissaoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissaoRepository extends JpaRepository<Missao, UUID> {
    List<Missao> findByPessoaIdOrderByCreatedAtDesc(String pessoaId);
    List<Missao> findByPessoaIdAndStatusOrderByCreatedAtDesc(String pessoaId, MissaoStatus status);
    Optional<Missao> findByIdAndPessoaId(UUID id, String pessoaId);
}
