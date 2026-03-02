package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.Medicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MedicaoRepository extends JpaRepository<Medicao, UUID> {}