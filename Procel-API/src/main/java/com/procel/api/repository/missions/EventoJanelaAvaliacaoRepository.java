package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoJanelaAvaliacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventoJanelaAvaliacaoRepository extends JpaRepository<EventoJanelaAvaliacao, UUID> {
    Optional<EventoJanelaAvaliacao> findByChaveIdempotencia(String chaveIdempotencia);
}
