package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoAvaliacaoRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventoAvaliacaoRequestRepository extends JpaRepository<EventoAvaliacaoRequest, UUID> {
    Optional<EventoAvaliacaoRequest> findByMedicaoId(UUID medicaoId);
}
