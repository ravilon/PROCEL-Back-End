package com.procel.ingestion.dto.missions;

import com.procel.ingestion.entity.missions.AtividadeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public class MissaoDTOs {

        @Schema(description = "Dados para criacao de um modelo de missao.")
        public record CreateMissaoRequest(
                        @Schema(description = "Titulo da missao.", example = "Inspecionar ocupacao da Sala 2") String titulo,
                        @Schema(description = "Descricao detalhada da missao.", example = "Verificar sensores e presenca atual antes do fechamento do turno.") String descricao,
                        @Schema(description = "Tipo da missao. Se omitido, usa Individual.", example = "Individual") String tipo,
                        @Schema(description = "Pontuacao de XP da missao. Informe apenas o valor numerico.", example = "20") Integer value,
                        @Schema(description = "Indica se a missao pode ser atribuida a pessoas. Se omitido, usa true.", example = "true") Boolean ativo,
                        @Schema(description = "Missao pai opcional.") UUID parentId) {
        }

        @Schema(description = "Dados para atualizacao de um modelo de missao.")
        public record UpdateMissaoRequest(
                        @Schema(description = "Titulo da missao.", example = "Inspecionar ocupacao da Sala 2") String titulo,
                        @Schema(description = "Descricao detalhada da missao.", example = "Verificar sensores e presenca atual antes do fechamento do turno.") String descricao,
                        @Schema(description = "Tipo da missao.", example = "Individual") String tipo,
                        @Schema(description = "Pontuacao de XP da missao. Informe apenas o valor numerico.", example = "20") Integer value,
                        @Schema(description = "Indica se a missao pode ser atribuida a pessoas.", example = "true") Boolean ativo,
                        @Schema(description = "Missao pai opcional. Envie null para manter o pai atual.") UUID parentId) {
        }

        @Schema(description = "Modelo de missao disponivel no sistema.")
        public record MissaoResponse(
                        @Schema(description = "Identificador da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") UUID id,
                        @Schema(description = "Titulo da missao.", example = "Inspecionar ocupacao da Sala 2") String titulo,
                        @Schema(description = "Descricao detalhada da missao.", example = "Verificar sensores e presenca atual antes do fechamento do turno.") String descricao,
                        @Schema(description = "Tipo da missao.", example = "Individual") String tipo,
                        @Schema(description = "Pontuacao de XP da missao.", example = "20") int value,
                        @Schema(description = "Indica se a missao pode ser atribuida a pessoas.", example = "true") boolean ativo,
                        @Schema(description = "Instante de criacao.", example = "2026-05-11T21:30:00Z") Instant createdAt,
                        @Schema(description = "Identificador da missao pai.") UUID parentId,
                        @Schema(description = "Titulo da missao pai.") String parentTitulo) {
        }

        @Schema(description = "Dados para atribuir uma missao a uma pessoa como atividade.")
        public record AtribuirMissaoRequest(
                        @Schema(description = "Identificador do modelo de missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") UUID missaoId,
                        @Schema(description = "Status inicial da atividade. Se omitido, usa PENDENTE.", example = "PENDENTE") AtividadeStatus status,
                        @Schema(description = "Instante de inicio planejado/registrado.", example = "2026-05-11T22:00:00Z") Instant startedAt) {
        }

        @Schema(description = "Dados para atualizar a atividade de uma pessoa.")
        public record UpdateAtividadeRequest(
                        @Schema(description = "Novo status da atividade.", example = "CONCLUIDA") AtividadeStatus status,
                        @Schema(description = "Instante de inicio.", example = "2026-05-11T22:00:00Z") Instant startedAt,
                        @Schema(description = "Instante de conclusao.", example = "2026-05-11T23:00:00Z") Instant completedAt) {
        }

        @Schema(description = "Atividade de uma pessoa gerada pela atribuicao de uma missao.")
        public record AtividadeResponse(
                        @Schema(description = "Identificador da atividade.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") UUID id,
                        @Schema(description = "Identificador da pessoa.", example = "ravilon") String pessoaId,
                        @Schema(description = "Nome da pessoa.", example = "Ravilon A. Santos") String pessoaNome,
                        @Schema(description = "Identificador da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") UUID missaoId,
                        @Schema(description = "Titulo da missao.", example = "Inspecionar ocupacao da Sala 2") String missaoTitulo,
                        @Schema(description = "Descricao da missao.", example = "Verificar sensores e presenca atual antes do fechamento do turno.") String missaoDescricao,
                        @Schema(description = "Tipo da missao.", example = "Individual") String missaoTipo,
                        @Schema(description = "Pontuacao XP da missao.", example = "20") int missaoValue,
                        @Schema(description = "Status da atividade.", example = "PENDENTE") AtividadeStatus status,
                        @Schema(description = "Instante de atribuicao.", example = "2026-05-11T21:30:00Z") Instant assignedAt,
                        @Schema(description = "Instante de inicio.", example = "2026-05-11T22:00:00Z") Instant startedAt,
                        @Schema(description = "Instante de conclusao.", example = "2026-05-11T23:00:00Z") Instant completedAt) {
        }

        @Schema(description = "Resumo quantitativo das atividades de uma pessoa por status.")
        public record AtividadesResumoResponse(
                        @Schema(description = "Total de atividades da pessoa.", example = "12") long total,
                        @Schema(description = "Atividades pendentes.", example = "4") long pendentes,
                        @Schema(description = "Atividades em andamento.", example = "2") long emAndamento,
                        @Schema(description = "Atividades concluidas.", example = "5") long concluidas,
                        @Schema(description = "Atividades expiradas.", example = "1") long expiradas,
                        @Schema(description = "Atividades canceladas.", example = "0") long canceladas) {
        }
}
