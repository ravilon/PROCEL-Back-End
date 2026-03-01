package com.procel.ingestion.repository;

import com.procel.ingestion.entity.Compartimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompartimentoRepository extends JpaRepository<Compartimento, UUID> {
    Optional<Compartimento> findByExternalId(Long externalId);
}