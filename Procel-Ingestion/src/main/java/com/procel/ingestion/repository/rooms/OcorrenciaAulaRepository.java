package com.procel.ingestion.repository.rooms;

import com.procel.ingestion.entity.rooms.OcorrenciaAula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OcorrenciaAulaRepository extends JpaRepository<OcorrenciaAula, UUID> {

    List<OcorrenciaAula> findByCompartimentoIdAndDataOrderByTurnoAscPeriodoAulaAsc(
            String compartimentoId,
            LocalDate data
    );

    List<OcorrenciaAula> findByDisciplinaIdAndDataOrderByTurnoAscPeriodoAulaAsc(
            Long disciplinaId,
            LocalDate data
    );

    long deleteByCompartimentoIdAndDataBetween(
            String compartimentoId,
            LocalDate dataInicial,
            LocalDate dataFinal
    );
}
