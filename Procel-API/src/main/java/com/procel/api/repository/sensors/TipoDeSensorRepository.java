package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.TipoDeSensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoDeSensorRepository extends JpaRepository<TipoDeSensor, String> {

    default Optional<TipoDeSensor> findByNome(String nome) {
        return findById(nome);
    }
}