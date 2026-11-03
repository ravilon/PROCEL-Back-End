package com.proben.api.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.proben.api.entity.rooms.Campus;

public interface CampusRepository extends JpaRepository<Campus, String> {}