package com.proben.api.repository.sensors;

import com.proben.api.entity.sensors.GrupoRegra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de grupo regra.
 */

public interface GrupoRegraRepository extends JpaRepository<GrupoRegra, UUID> {
}
