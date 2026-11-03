package com.proben.api.dto.environment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EnvironmentRuleDTOs {
    private EnvironmentRuleDTOs() {}

    public record RuleRequest(
            @NotBlank String nome, String descricao, @NotBlank String categoria,
            Boolean ativo, Boolean padrao, @NotBlank String escopo,
            String campusNome, String compartimentoId, String timezone,
            String cronExpression, List<Integer> diasSemana,
            Instant vigenciaInicio, Instant vigenciaFim,
            @NotBlank String politicaOcupacao,
            @NotBlank String politicaBeneficiario,
            Boolean monitorarSemPontos,
            @NotNull JsonNode pontuacao,
            @NotNull JsonNode configuracao
    ) {}

    public record RuleResponse(
            UUID id, String nome, String descricao, String categoria,
            boolean ativo, boolean padrao, String escopo,
            String campusNome, String compartimentoId, String timezone,
            String cronExpression, List<Integer> diasSemana,
            Instant vigenciaInicio, Instant vigenciaFim,
            String politicaOcupacao, String politicaBeneficiario,
            boolean monitorarSemPontos, JsonNode pontuacao, JsonNode configuracao,
            int versao, Instant createdAt, String createdBy,
            Instant updatedAt, String updatedBy
    ) {}

    public record ResolutionResponse(
            String compartimentoId, String categoria, RuleResponse specific,
            RuleResponse campusDefault, RuleResponse globalDefault,
            RuleResponse effective, String origem
    ) {}

    public record ExecutionResponse(
            UUID id, UUID regraId, int regraVersao, String compartimentoId,
            String categoria, String origem, String tipoExecucao,
            Instant instantePrevisto, Instant instanteReal, String timezone,
            String status, String resultado, String motivo,
            JsonNode evidencias, JsonNode contextoAcademico, JsonNode ocupacao,
            JsonNode beneficiarios, int pontosCalculados, JsonNode lancamentos,
            int attempts, Instant leaseUntil, Long durationMs, Instant createdAt
    ) {}

    public record CopyRequest(@NotNull List<String> compartimentoIds) {}
    public record DeletePreview(UUID id, long executionCount, boolean deletable, String recommendation) {}
    public record TestRequest(@NotBlank String compartimentoId, Instant instante) {}
    public record TestResponse(String resultado, String motivo, JsonNode evidencias,
                               JsonNode contextoAcademico, JsonNode ocupacao,
                               JsonNode beneficiarios, int pontosCalculados) {}
}
