package com.procel.ingestion.repository.sensors;

import org.springframework.data.jpa.repository.JpaRepository;

import com.procel.ingestion.entity.sensors.TipoDeSensor;

import java.util.Optional;
import java.util.UUID;

public interface TipoDeSensorRepository extends JpaRepository<TipoDeSensor, UUID> {
 
    Optional<TipoDeSensor> findByNome(String nome);
}