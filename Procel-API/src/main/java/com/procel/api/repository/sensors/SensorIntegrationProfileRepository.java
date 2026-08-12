package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.SensorIntegrationProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SensorIntegrationProfileRepository extends JpaRepository<SensorIntegrationProfile, UUID> {
    Optional<SensorIntegrationProfile> findByNome(String nome);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SensorIntegrationProfile p where p.id = :id")
    Optional<SensorIntegrationProfile> findByIdForUpdate(@Param("id") UUID id);
}
