package com.procel.ingestion.repository;

import com.procel.ingestion.entity.Unidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnidadeRepository extends JpaRepository<Unidade, UUID> {
    Optional<Unidade> findByCodigo(String codigo);
}