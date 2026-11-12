package com.proben.api.repository.rooms;

import com.proben.api.entity.rooms.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de acesso e consulta aos dados de disciplina.
 */

public interface DisciplinaRepository extends JpaRepository<Disciplina, Long> {}
