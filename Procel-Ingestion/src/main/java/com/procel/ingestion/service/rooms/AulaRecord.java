package com.procel.ingestion.service.rooms;

import com.procel.ingestion.entity.rooms.OcorrenciaAulaTipo;

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
        OcorrenciaAulaTipo tipo,
        String descricao
) {}
