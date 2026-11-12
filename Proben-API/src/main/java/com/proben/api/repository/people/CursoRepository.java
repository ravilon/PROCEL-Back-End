package com.proben.api.repository.people;

import com.proben.api.entity.people.Curso;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de acesso e consulta aos dados de curso.
 */

public interface CursoRepository extends JpaRepository<Curso, Long> {
}
