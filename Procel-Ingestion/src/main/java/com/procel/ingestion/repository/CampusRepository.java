package com.procel.ingestion.repository;

import com.procel.ingestion.entity.Campus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CampusRepository extends JpaRepository<Campus, UUID> {
    Optional<Campus> findByNome(String nome);
}