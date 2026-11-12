package com.proben.api.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.proben.api.entity.rooms.Predio;
import java.util.Optional;

/**
 * Repositorio de acesso e consulta aos dados de predio.
 */

public interface PredioRepository extends JpaRepository<Predio, String> {
    Optional<Predio> findByCampus_NomeAndNome(String campusNome, String nome);
}