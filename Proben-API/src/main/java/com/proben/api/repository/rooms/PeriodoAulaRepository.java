package com.proben.api.repository.rooms;

import com.proben.api.entity.rooms.PeriodoAula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de periodo aula.
 */

public interface PeriodoAulaRepository extends JpaRepository<PeriodoAula, UUID> {

    List<PeriodoAula> findByCompartimentoIdAndDataOrderByTurnoAscPeriodoAulaAsc(
            String compartimentoId,
            LocalDate data
    );

    List<PeriodoAula> findByDisciplinaIdAndDataOrderByTurnoAscPeriodoAulaAsc(
            Long disciplinaId,
            LocalDate data
    );

    List<PeriodoAula> findByCompartimentoIdAndDataAndHoraInicioLessThanEqualAndHoraFimGreaterThanOrderByTurnoAscPeriodoAulaAsc(
            String compartimentoId,
            LocalDate data,
            LocalTime hora,
            LocalTime mesmaHora
    );

    List<PeriodoAula> findTop200ByCompartimentoIdOrderByDataDescTurnoAscPeriodoAulaAsc(
            String compartimentoId
    );

    List<PeriodoAula> findTop200ByDisciplinaIdOrderByDataDescTurnoAscPeriodoAulaAsc(
            Long disciplinaId
    );

    long deleteByCompartimentoIdAndDataBetween(
            String compartimentoId,
            LocalDate dataInicial,
            LocalDate dataFinal
    );
}
