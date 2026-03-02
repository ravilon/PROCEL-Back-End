package com.procel.ingestion.repository.sensors;

import org.springframework.data.jpa.repository.JpaRepository;

import com.procel.ingestion.entity.sensors.ParametroDef;

import java.util.Optional;
import java.util.UUID;

public interface ParametroDefRepository extends JpaRepository<ParametroDef, UUID> {
    Optional<ParametroDef> findByTipo_IdAndNome(UUID tipoId, String nome);
}