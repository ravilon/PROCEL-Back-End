package com.procel.ingestion.repository.missions;

import com.procel.ingestion.entity.missions.Missao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MissaoRepository extends JpaRepository<Missao, UUID> {
    List<Missao> findByAtivoOrderByCreatedAtDesc(boolean ativo);
    List<Missao> findAllByOrderByCreatedAtDesc();
}
