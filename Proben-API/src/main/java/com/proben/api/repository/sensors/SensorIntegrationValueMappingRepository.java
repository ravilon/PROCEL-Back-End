package com.proben.api.repository.sensors;

import com.proben.api.entity.sensors.SensorIntegrationValueMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de sensor integration value mapping.
 */

public interface SensorIntegrationValueMappingRepository extends JpaRepository<SensorIntegrationValueMapping, UUID> {
    List<SensorIntegrationValueMapping> findAllByParserVersion_IdOrderByParameterNameAsc(UUID parserVersionId);
    void deleteAllByParserVersion_Id(UUID parserVersionId);
}
