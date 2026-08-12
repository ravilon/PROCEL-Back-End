package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.SensorIntegrationBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SensorIntegrationBindingRepository extends JpaRepository<SensorIntegrationBinding, UUID> {
    Optional<SensorIntegrationBinding> findByProfile_IdAndSensor_ExternalIdAndAtivoTrue(UUID profileId, String sensorExternalId);
    Optional<SensorIntegrationBinding> findFirstByProfile_IdAndSensor_ExternalIdOrderByCreatedAtDesc(UUID profileId, String sensorExternalId);
    List<SensorIntegrationBinding> findAllByProfile_IdOrderByCreatedAtDesc(UUID profileId);
    List<SensorIntegrationBinding> findAllByProfile_IdAndAtivoTrueOrderByCreatedAtDesc(UUID profileId);
    List<SensorIntegrationBinding> findAllByAtivoTrueAndProfile_AtivoTrueAndSensor_AtivoTrue();
}
