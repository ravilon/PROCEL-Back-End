package com.proben.api.repository.missions;

import com.proben.api.entity.missions.EventoOcorrenciaEvidencia;
import com.proben.api.entity.missions.EventoOcorrenciaEvidenciaPapel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoOcorrenciaEvidenciaRepository extends JpaRepository<EventoOcorrenciaEvidencia, UUID> {
    Page<EventoOcorrenciaEvidencia> findByEventoOcorrenciaId(UUID eventoOcorrenciaId, Pageable pageable);

    List<EventoOcorrenciaEvidencia> findByEventoOcorrenciaIdOrderByCreatedAtAsc(UUID eventoOcorrenciaId);

    Optional<EventoOcorrenciaEvidencia> findByEventoOcorrenciaIdAndMedicaoIdAndParametroValorIdAndPapel(
            UUID eventoOcorrenciaId,
            UUID medicaoId,
            UUID parametroValorId,
            EventoOcorrenciaEvidenciaPapel papel
    );

    Optional<EventoOcorrenciaEvidencia> findByEventoOcorrenciaIdAndMedicaoIdAndParametroValorIsNullAndPapel(
            UUID eventoOcorrenciaId,
            UUID medicaoId,
            EventoOcorrenciaEvidenciaPapel papel
    );
}
