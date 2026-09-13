package com.procel.api.repository.missions;

import com.procel.api.entity.missions.AtividadeEvento;
import com.procel.api.entity.missions.AtividadeEventoTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AtividadeEventoRepository extends JpaRepository<AtividadeEvento, UUID> {
    Optional<AtividadeEvento> findByAtividadeIdAndEventoOcorrenciaIdAndTipo(
            UUID atividadeId,
            UUID eventoOcorrenciaId,
            AtividadeEventoTipo tipo
    );
}
