package com.procel.api.repository.missions;

import com.procel.api.entity.missions.Missao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MissaoRepository extends JpaRepository<Missao, UUID> {
    List<Missao> findByAtivoOrderByCreatedAtDesc(boolean ativo);
    List<Missao> findAllByOrderByCreatedAtDesc();
    List<Missao> findByParent_IdOrderByCreatedAtAsc(UUID parentId);
}
