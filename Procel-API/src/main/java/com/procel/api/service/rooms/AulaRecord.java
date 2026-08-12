package com.procel.api.service.rooms;

import com.procel.api.entity.rooms.PeriodoAulaTipo;

import java.time.LocalDate;
import java.time.LocalTime;

public record AulaRecord(
        Long disciplinaId,
        String disciplinaNome,
        String unidadeSigla,
        LocalDate data,
        Integer turno,
        Integer periodoAula,
        LocalTime horaInicio,
        LocalTime horaFim,
        String turma,
        PeriodoAulaTipo tipo,
        String descricao
) {}
