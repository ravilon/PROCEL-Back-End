package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface SensorRepository extends JpaRepository<Sensor, String> {
    List<Sensor> findByCompartimentoIdOrderByNomeAsc(String compartimentoId);

    default Optional<Sensor> findByExternalId(String externalId) {
        return findById(externalId);
    }
}
