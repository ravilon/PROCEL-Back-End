package com.proben.api.repository.missions;

import com.proben.api.entity.missions.AtividadeEvento;
import com.proben.api.entity.missions.AtividadeEventoTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de atividade evento.
 */

public interface AtividadeEventoRepository extends JpaRepository<AtividadeEvento, UUID> {
    Optional<AtividadeEvento> findByAtividadeIdAndEventoOcorrenciaIdAndTipo(
            UUID atividadeId,
            UUID eventoOcorrenciaId,
            AtividadeEventoTipo tipo
    );
}
