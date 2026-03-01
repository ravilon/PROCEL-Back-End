package com.procel.ingestion.repository;

import com.procel.ingestion.entity.Predio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PredioRepository extends JpaRepository<Predio, UUID> {
    Optional<Predio> findByCampus_IdAndNome(UUID campusId, String nome);
}