package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.Medicao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MedicaoRepository extends JpaRepository<Medicao, UUID>, JpaSpecificationExecutor<Medicao> {

    @Query("""
           select m from Medicao m
           join m.sensor s
           where s.externalId = :sensorExternalId
             and (:from is null or m.timestamp >= :from)
             and (:to   is null or m.timestamp <= :to)
           order by m.timestamp desc
           """)
    List<Medicao> findBySensorExternalId(
            @Param("sensorExternalId") String sensorExternalId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
           select m from Medicao m
           join m.sensor s
           join s.compartimento c
           where c.id = :compartimentoId
             and (:from is null or m.timestamp >= :from)
             and (:to   is null or m.timestamp <= :to)
           order by m.timestamp desc
           """)
    List<Medicao> findByCompartimentoId(
            @Param("compartimentoId") String compartimentoId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
           select m from Medicao m
           join m.sensor s
           where s.externalId = :sensorExternalId
           order by m.timestamp desc
           """)
    List<Medicao> findLatestBySensorExternalId(
            @Param("sensorExternalId") String sensorExternalId,
            Pageable pageable
    );

    @Query("""
           select m from Medicao m
           join m.sensor s
           join s.compartimento c
           where c.id = :compartimentoId
           order by m.timestamp desc
           """)
    List<Medicao> findLatestByCompartimentoId(
            @Param("compartimentoId") String compartimentoId,
            Pageable pageable
    );
}