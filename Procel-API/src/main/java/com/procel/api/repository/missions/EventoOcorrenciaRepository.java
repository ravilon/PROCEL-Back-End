package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventoOcorrenciaRepository extends JpaRepository<EventoOcorrencia, UUID> {
    Optional<EventoOcorrencia> findByChaveIdempotencia(String chaveIdempotencia);
}
