package com.proben.api.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.proben.api.entity.rooms.Campus;

/**
 * Repositorio de acesso e consulta aos dados de campus.
 */

public interface CampusRepository extends JpaRepository<Campus, String> {}