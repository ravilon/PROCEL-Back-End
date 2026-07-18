package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.RegraParametro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegraParametroRepository extends JpaRepository<RegraParametro, UUID> {

    List<RegraParametro> findAllByGrupoRegra_IdOrderByPrioridadeDescSeveridadeDesc(UUID grupoRegraId);

    List<RegraParametro> findAllByGrupoRegra_IdAndAtivoTrueOrderByPrioridadeDescSeveridadeDesc(UUID grupoRegraId);

    List<RegraParametro> findAllByGrupoRegra_IdAndParametroDef_IdAndAtivoTrue(UUID grupoRegraId, UUID parametroDefId);

    boolean existsByGrupoRegra_IdAndParametroDef_IdAndAtivoTrue(UUID grupoRegraId, UUID parametroDefId);
}
