package com.procel.ingestion.repository.sensors;

import org.springframework.data.jpa.repository.JpaRepository;

import com.procel.ingestion.entity.sensors.Medicao;

import java.util.UUID;

public interface MedicaoRepository extends JpaRepository<Medicao, UUID> {}