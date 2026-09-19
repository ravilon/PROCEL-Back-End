package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoCondicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoCondicaoRepository extends JpaRepository<EventoCondicao, UUID> {
    List<EventoCondicao> findByEventoDefinicaoIdAndAtivoTrueOrderByOrdemAscCreatedAtAsc(UUID eventoDefinicaoId);
    List<EventoCondicao> findByEventoDefinicaoIdOrderByOrdemAscCreatedAtAsc(UUID eventoDefinicaoId);
    boolean existsByEventoDefinicaoIdAndAtivoTrueAndOrdem(UUID eventoDefinicaoId, Integer ordem);
    boolean existsByEventoDefinicaoIdAndAtivoTrueAndOrdemAndIdNot(UUID eventoDefinicaoId, Integer ordem, UUID id);
}
