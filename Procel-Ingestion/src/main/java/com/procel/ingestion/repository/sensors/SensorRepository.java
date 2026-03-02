package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SensorRepository extends JpaRepository<Sensor, String> {
    default Optional<Sensor> findByExternalId(String externalId) {
        return findById(externalId);
    }
}