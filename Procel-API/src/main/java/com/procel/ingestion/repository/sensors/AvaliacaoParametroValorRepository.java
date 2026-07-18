package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.AvaliacaoParametroValor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AvaliacaoParametroValorRepository extends JpaRepository<AvaliacaoParametroValor, UUID> {

    List<AvaliacaoParametroValor> findAllByParametroValor_IdIn(Collection<UUID> parametroValorIds);
}
