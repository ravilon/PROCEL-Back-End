package com.proben.api.service.rooms;

import com.proben.api.entity.rooms.PeriodoAulaTipo;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Dados de uma ocorrência de aula normalizados para sincronização acadêmica.
 *
 * @param disciplinaId identificador da disciplina na origem
 * @param disciplinaNome nome da disciplina
 * @param unidadeSigla sigla da unidade acadêmica
 * @param data data da aula
 * @param turno código do turno informado pela origem
 * @param periodoAula número do período da aula
 * @param horaInicio horário de início da aula
 * @param horaFim horário de término da aula
 * @param turma identificação da turma
 * @param tipo tipo de período de aula
 * @param descricao descrição adicional da ocorrência
 */

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
