package com.procel.ingestion.repository.sensors;

import org.springframework.data.jpa.repository.JpaRepository;

import com.procel.ingestion.entity.sensors.Sensor;

import java.util.Optional;
import java.util.UUID;

public interface SensorRepository extends JpaRepository<Sensor, UUID> {
    Optional<Sensor> findByExternalId(String externalId);
}