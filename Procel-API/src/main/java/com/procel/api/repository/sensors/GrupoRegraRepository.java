package com.procel.api.repository.sensors;

import com.procel.api.entity.sensors.GrupoRegra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GrupoRegraRepository extends JpaRepository<GrupoRegra, UUID> {
}
