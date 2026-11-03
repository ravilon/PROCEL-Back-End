package com.proben.api.repository.sensors;

import com.proben.api.entity.sensors.GrupoRegra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GrupoRegraRepository extends JpaRepository<GrupoRegra, UUID> {
}
