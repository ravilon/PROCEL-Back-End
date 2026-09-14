package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoJanelaEvidencia;
import com.procel.api.entity.missions.EventoJanelaEvidenciaPapel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventoJanelaEvidenciaRepository extends JpaRepository<EventoJanelaEvidencia, UUID> {
    Page<EventoJanelaEvidencia> findByJanelaAvaliacaoId(UUID janelaAvaliacaoId, Pageable pageable);

    Optional<EventoJanelaEvidencia> findByJanelaAvaliacaoIdAndMedicaoIdAndParametroValorIdAndPapel(
            UUID janelaAvaliacaoId,
            UUID medicaoId,
            UUID parametroValorId,
            EventoJanelaEvidenciaPapel papel
    );

    Optional<EventoJanelaEvidencia> findByJanelaAvaliacaoIdAndMedicaoIdAndParametroValorIsNullAndPapel(
            UUID janelaAvaliacaoId,
            UUID medicaoId,
            EventoJanelaEvidenciaPapel papel
    );
}
