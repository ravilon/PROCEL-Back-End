package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoAvaliacaoRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EventoAvaliacaoRequestRepository extends JpaRepository<EventoAvaliacaoRequest, UUID>, JpaSpecificationExecutor<EventoAvaliacaoRequest> {
    Optional<EventoAvaliacaoRequest> findByMedicaoId(UUID medicaoId);
}
