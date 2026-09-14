package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoJanelaAvaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EventoJanelaAvaliacaoRepository extends JpaRepository<EventoJanelaAvaliacao, UUID>, JpaSpecificationExecutor<EventoJanelaAvaliacao> {
    Optional<EventoJanelaAvaliacao> findByChaveIdempotencia(String chaveIdempotencia);
}
