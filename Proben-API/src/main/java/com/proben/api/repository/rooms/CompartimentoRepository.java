package com.proben.api.repository.rooms;

import com.proben.api.entity.rooms.Compartimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repositorio de acesso e consulta aos dados de compartimento.
 */

public interface CompartimentoRepository extends JpaRepository<Compartimento, String> {
    List<Compartimento> findByTipoIn(Collection<String> tipos);
}
