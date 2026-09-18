package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.AvaliacaoParametroValor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AvaliacaoParametroValorRepository extends JpaRepository<AvaliacaoParametroValor, UUID> {

    List<AvaliacaoParametroValor> findAllByParametroValor_IdIn(Collection<UUID> parametroValorIds);

    long deleteByRegraParametro_Id(UUID regraParametroId);

    long deleteByRegraParametro_GrupoRegra_Id(UUID grupoRegraId);
}
