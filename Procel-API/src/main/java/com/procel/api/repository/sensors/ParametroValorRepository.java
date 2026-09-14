package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.ParametroValor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParametroValorRepository extends JpaRepository<ParametroValor, UUID> {
    Optional<ParametroValor> findByMedicao_IdAndParametroDef_Id(UUID medicaoId, UUID parametroDefId);
     List<ParametroValor> findAllByMedicao_IdIn(Collection<UUID> medicaoIds);
    List<ParametroValor> findAllByMedicao_Id(UUID medicaoId);

    @Query("""
           select pv
           from ParametroValor pv
           join fetch pv.medicao m
           join fetch pv.parametroDef pd
           join fetch m.sensor s
           left join fetch s.compartimento c
           where s.externalId = :sensorExternalId
             and pd.id = :parametroDefId
             and m.timestamp < :before
           order by m.timestamp desc, m.recebidoEm desc, m.id desc
           """)
    List<ParametroValor> findPreviousForSensorParameter(
            @Param("sensorExternalId") String sensorExternalId,
            @Param("parametroDefId") UUID parametroDefId,
            @Param("before") Instant before,
            Pageable pageable
    );
}
