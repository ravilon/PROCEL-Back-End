package com.proben.api.repository.shared;

import com.proben.api.entity.shared.ContextoCompartilhado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de contexto compartilhado.
 */

public interface ContextoCompartilhadoRepository extends JpaRepository<ContextoCompartilhado, UUID> {}
