package com.proben.api.repository.missions;

import com.proben.api.entity.missions.EventoOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de evento ocorrencia.
 */

public interface EventoOcorrenciaRepository extends JpaRepository<EventoOcorrencia, UUID>, JpaSpecificationExecutor<EventoOcorrencia> {
    Optional<EventoOcorrencia> findByChaveIdempotencia(String chaveIdempotencia);
}
