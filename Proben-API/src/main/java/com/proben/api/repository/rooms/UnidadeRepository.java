package com.proben.api.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.proben.api.entity.rooms.Unidade;

/**
 * Repositorio de acesso e consulta aos dados de unidade.
 */

public interface UnidadeRepository extends JpaRepository<Unidade, String> {}