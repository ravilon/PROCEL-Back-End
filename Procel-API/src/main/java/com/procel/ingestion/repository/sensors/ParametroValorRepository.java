package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.ParametroValor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParametroValorRepository extends JpaRepository<ParametroValor, UUID> {
    Optional<ParametroValor> findByMedicao_IdAndParametroDef_Id(UUID medicaoId, UUID parametroDefId);
     List<ParametroValor> findAllByMedicao_IdIn(Collection<UUID> medicaoIds);
}