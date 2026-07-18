package com.procel.ingestion.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.procel.ingestion.entity.rooms.Unidade;

public interface UnidadeRepository extends JpaRepository<Unidade, String> {}