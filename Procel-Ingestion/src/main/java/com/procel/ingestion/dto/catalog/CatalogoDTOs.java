package com.procel.ingestion.dto.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import java.util.List;

public final class CatalogoDTOs {

    private CatalogoDTOs() {}

    public record CompartimentoResponse(
            String id,
            String nome,
            String tipo,
            Integer pavimento,
            Integer capacidade,
            BigDecimal area,
            String predioId,
            String predioNome,
            String campusNome,
            String unidadeNome
    ) {}

    public record CompartimentoFilterOptionsResponse(
            List<String> tipos,
            List<String> predios,
            List<String> unidades,
            List<String> campi
    ) {}

    public record SensorResponse(
            String externalId,
            String nome,
            String tipoNome,
            String compartimentoId,
            String compartimentoNome,
            boolean ativo
    ) {}

    public record DisciplinaResponse(
            Long id,
            String nome,
            String unidadeSigla
    ) {}

    public record PeriodoAulaResponse(
            UUID id,
            String compartimentoId,
            String compartimentoNome,
            Long disciplinaId,
            String disciplinaNome,
            LocalDate data,
            Integer turno,
            Integer periodoAula,
            LocalTime horaInicio,
            LocalTime horaFim,
            String turma,
            String tipo,
            String descricao,
            String source,
            Instant sincronizadoEm
    ) {}

    public record PessoaResumoResponse(
            String id,
            String nome,
            String email,
            String matricula,
            Set<String> roles
    ) {}
}
