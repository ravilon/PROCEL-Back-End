package com.procel.api.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.procel.api.entity.rooms.Campus;

public interface CampusRepository extends JpaRepository<Campus, String> {}