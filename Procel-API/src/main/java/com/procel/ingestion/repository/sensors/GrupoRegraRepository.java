package com.procel.ingestion.repository.sensors;

import com.procel.ingestion.entity.sensors.GrupoRegra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GrupoRegraRepository extends JpaRepository<GrupoRegra, UUID> {
}
