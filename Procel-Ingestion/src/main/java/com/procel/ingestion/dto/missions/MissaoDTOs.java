package com.procel.ingestion.dto.missions;

import com.procel.ingestion.entity.missions.MissaoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public class MissaoDTOs {

    @Schema(description = "Dados para criacao de missao vinculada a uma pessoa.")
    public record CreateMissaoRequest(
            @Schema(description = "Titulo da missao.", example = "Inspecionar ocupacao da Sala 2")
            String titulo,
            @Schema(description = "Descricao detalhada da missao.", example = "Verificar sensores e presenca atual antes do fechamento do turno.")
            String descricao,
            @Schema(description = "Status inicial. Se omitido, usa PENDENTE.", example = "PENDENTE")
            MissaoStatus status,
            @Schema(description = "Instante de inicio planejado/registrado.", example = "2026-05-11T22:00:00Z")
            Instant startedAt
    ) {}

    @Schema(description = "Dados para atualizacao de missao.")
    public record UpdateMissaoRequest(
            @Schema(description = "Titulo da missao.", example = "Inspecionar ocupacao da Sala 2")
            String titulo,
            @Schema(description = "Descricao detalhada da missao.", example = "Verificar sensores e presenca atual antes do fechamento do turno.")
            String descricao,
            @Schema(description = "Novo status da missao.", example = "CONCLUIDA")
            MissaoStatus status,
            @Schema(description = "Instante de inicio.", example = "2026-05-11T22:00:00Z")
            Instant startedAt,
            @Schema(description = "Instante de conclusao.", example = "2026-05-11T23:00:00Z")
            Instant completedAt
    ) {}

    @Schema(description = "Missao vinculada a uma pessoa.")
    public record MissaoResponse(
            @Schema(description = "Identificador da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6")
            UUID id,
            @Schema(description = "Identificador da pessoa.", example = "ravilon")
            String pessoaId,
            @Schema(description = "Nome da pessoa.", example = "Ravilon A. Santos")
            String pessoaNome,
            @Schema(description = "Titulo da missao.", example = "Inspecionar ocupacao da Sala 2")
            String titulo,
            @Schema(description = "Descricao detalhada da missao.", example = "Verificar sensores e presenca atual antes do fechamento do turno.")
            String descricao,
            @Schema(description = "Status da missao.", example = "PENDENTE")
            MissaoStatus status,
            @Schema(description = "Instante de criacao.", example = "2026-05-11T21:30:00Z")
            Instant createdAt,
            @Schema(description = "Instante de inicio.", example = "2026-05-11T22:00:00Z")
            Instant startedAt,
            @Schema(description = "Instante de conclusao.", example = "2026-05-11T23:00:00Z")
            Instant completedAt
    ) {}
}
