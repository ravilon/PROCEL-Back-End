package com.proben.api.repository.sensors;

import com.proben.api.entity.sensors.TipoDeSensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio de acesso e consulta aos dados de tipo de sensor.
 */

public interface TipoDeSensorRepository extends JpaRepository<TipoDeSensor, String> {

    default Optional<TipoDeSensor> findByNome(String nome) {
        return findById(nome);
    }
}