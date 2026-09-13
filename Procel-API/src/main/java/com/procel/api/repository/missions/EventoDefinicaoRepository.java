package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoDefinicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoDefinicaoRepository extends JpaRepository<EventoDefinicao, UUID> {
    List<EventoDefinicao> findByMissaoIdOrderByOrdemAscCreatedAtAsc(UUID missaoId);
}
