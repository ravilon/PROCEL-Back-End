package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.ParametroDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParametroDefRepository extends JpaRepository<ParametroDef, UUID> {

    Optional<ParametroDef> findByTipo_NomeAndNome(String tipoNome, String nome);

    List<ParametroDef> findAllByTipo_Nome(String tipoNome);
}