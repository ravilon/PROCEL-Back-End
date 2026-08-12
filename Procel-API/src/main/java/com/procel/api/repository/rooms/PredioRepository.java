package com.procel.api.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.procel.api.entity.rooms.Predio;
import java.util.Optional;

public interface PredioRepository extends JpaRepository<Predio, String> {
    Optional<Predio> findByCampus_NomeAndNome(String campusNome, String nome);
}