package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EventoOcorrenciaRepository extends JpaRepository<EventoOcorrencia, UUID>, JpaSpecificationExecutor<EventoOcorrencia> {
    Optional<EventoOcorrencia> findByChaveIdempotencia(String chaveIdempotencia);
}
