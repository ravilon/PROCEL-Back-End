package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.SensorGrupoRegra;
import com.procel.ingestion.entity.sensors.SensorGrupoRegraStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SensorGrupoRegraRepository extends JpaRepository<SensorGrupoRegra, UUID> {

    List<SensorGrupoRegra> findAllBySensor_ExternalIdOrderByCreatedAtDesc(String sensorExternalId);

    List<SensorGrupoRegra> findAllBySensor_ExternalIdAndStatus(String sensorExternalId, SensorGrupoRegraStatus status);

    @Query("""
            select sgr
            from SensorGrupoRegra sgr
            join fetch sgr.grupoRegra gr
            where sgr.sensor.externalId = :sensorExternalId
              and sgr.status = :status
              and gr.ativo = true
              and (sgr.validoDe is null or sgr.validoDe <= :timestamp)
              and (sgr.validoAte is null or sgr.validoAte > :timestamp)
            order by sgr.validoDe desc, sgr.createdAt desc
            """)
    List<SensorGrupoRegra> findEffectiveGroups(
            @Param("sensorExternalId") String sensorExternalId,
            @Param("status") SensorGrupoRegraStatus status,
            @Param("timestamp") Instant timestamp
    );
}
