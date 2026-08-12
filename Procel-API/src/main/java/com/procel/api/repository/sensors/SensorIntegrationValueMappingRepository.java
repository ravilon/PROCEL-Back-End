package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.SensorIntegrationValueMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SensorIntegrationValueMappingRepository extends JpaRepository<SensorIntegrationValueMapping, UUID> {
    List<SensorIntegrationValueMapping> findAllByParserVersion_IdOrderByParameterNameAsc(UUID parserVersionId);
}
