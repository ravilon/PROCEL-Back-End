package com.procel.ingestion.repository.sensors;

import org.springframework.data.jpa.repository.JpaRepository;

import com.procel.ingestion.entity.sensors.ParametroValor;

import java.util.Optional;
import java.util.UUID;

public interface ParametroValorRepository extends JpaRepository<ParametroValor, UUID> {
    Optional<ParametroValor> findByMedicao_IdAndParametroDef_Id(UUID medicaoId, UUID parametroDefId);
}