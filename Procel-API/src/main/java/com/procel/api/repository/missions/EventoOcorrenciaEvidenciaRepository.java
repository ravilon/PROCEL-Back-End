package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoOcorrenciaEvidencia;
import com.procel.api.entity.missions.EventoOcorrenciaEvidenciaPapel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoOcorrenciaEvidenciaRepository extends JpaRepository<EventoOcorrenciaEvidencia, UUID> {
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
