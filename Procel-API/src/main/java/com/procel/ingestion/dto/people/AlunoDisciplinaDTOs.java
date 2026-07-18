package com.procel.ingestion.dto.people;

import com.procel.ingestion.entity.people.AlunoDisciplinaStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public class AlunoDisciplinaDTOs {

    @Schema(description = "Dados para vincular uma disciplina ao aluno.")
    public record VincularDisciplinaRequest(
            @Schema(description = "Identificador da disciplina.", example = "27064")
            Long disciplinaId,
            @Schema(description = "Turma cursada pelo aluno.", example = "T1")
            String turma,
            @Schema(description = "Periodo letivo no formato AAAA/S.", example = "2026/1")
            String periodoLetivo,
            @Schema(description = "Status inicial. Se omitido, assume ATIVA.", example = "ATIVA")
            AlunoDisciplinaStatus status
    ) {}

    @Schema(description = "Atualizacao do status do vinculo.")
    public record AtualizarVinculoRequest(
            @Schema(description = "Novo status do vinculo.", example = "CONCLUIDA")
            AlunoDisciplinaStatus status
    ) {}

    @Schema(description = "Disciplina vinculada ao aluno em um periodo letivo.")
    public record DisciplinaAlunoResponse(
            @Schema(description = "Identificador do vinculo.")
            UUID vinculoId,
            @Schema(description = "Identificador da pessoa.", example = "ravilon")
            String pessoaId,
            @Schema(description = "Identificador da disciplina.", example = "27064")
            Long disciplinaId,
            @Schema(description = "Nome da disciplina.", example = "SISTEMAS DISCRETOS")
            String disciplinaNome,
            @Schema(description = "Sigla da unidade.", example = "CDTEC")
            String unidadeSigla,
            @Schema(description = "Turma cursada.", example = "T1")
            String turma,
            @Schema(description = "Periodo letivo.", example = "2026/1")
            String periodoLetivo,
            @Schema(description = "Status do vinculo.", example = "ATIVA")
            AlunoDisciplinaStatus status,
            @Schema(description = "Instante de criacao do vinculo.")
            Instant vinculadoEm
    ) {}
}

