package com.proben.api.repository.missions;

import com.proben.api.entity.missions.EventoJanelaAvaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de evento janela avaliacao.
 */

public interface EventoJanelaAvaliacaoRepository extends JpaRepository<EventoJanelaAvaliacao, UUID>, JpaSpecificationExecutor<EventoJanelaAvaliacao> {
    Optional<EventoJanelaAvaliacao> findByChaveIdempotencia(String chaveIdempotencia);
}
