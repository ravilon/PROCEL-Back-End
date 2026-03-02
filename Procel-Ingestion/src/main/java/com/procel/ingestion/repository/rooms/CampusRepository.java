package com.procel.ingestion.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.procel.ingestion.entity.rooms.Campus;

public interface CampusRepository extends JpaRepository<Campus, String> {}