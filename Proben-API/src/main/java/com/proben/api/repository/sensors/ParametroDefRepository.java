package com.proben.api.repository.sensors;

import com.proben.api.entity.sensors.ParametroDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de parametro def.
 */

public interface ParametroDefRepository extends JpaRepository<ParametroDef, UUID> {

    Optional<ParametroDef> findByTipo_NomeAndNome(String tipoNome, String nome);

    List<ParametroDef> findAllByTipo_Nome(String tipoNome);

    Optional<ParametroDef> findByTipo_NomeAndNomeAndAtivoTrue(String tipoNome, String nome);

    List<ParametroDef> findAllByTipo_NomeAndAtivoTrue(String tipoNome);
}
