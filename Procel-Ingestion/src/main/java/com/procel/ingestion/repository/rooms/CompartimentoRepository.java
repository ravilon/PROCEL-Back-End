package com.procel.ingestion.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.procel.ingestion.entity.rooms.Compartimento;
import java.util.Optional;

public interface CompartimentoRepository extends JpaRepository<Compartimento, Long> {
    Optional<Compartimento> findByExternalId(Long externalId);
}