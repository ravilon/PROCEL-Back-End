package com.procel.ingestion.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.procel.ingestion.entity.rooms.Predio;
import java.util.Optional;

public interface PredioRepository extends JpaRepository<Predio, Long> {
    Optional<Predio> findByCampus_IdAndNome(Long campusId, String nome);
}