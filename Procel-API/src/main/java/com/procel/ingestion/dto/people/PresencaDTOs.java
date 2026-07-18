package com.procel.ingestion.dto.people;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public class PresencaDTOs {

    @Schema(description = "Dados para check-in em um compartimento.")
    public record CheckinRequest(
            @Schema(description = "Pessoa alvo. Para USUARIO comum, o backend ignora este campo e usa o subject do JWT.", example = "ravilon")
            String pessoaId,
            @Schema(description = "Identificador do compartimento/sala.", example = "2")
            String compartimentoId,
            @Schema(description = "Instante do check-in. Se omitido, usa o horario atual.", example = "2026-04-18T20:00:00Z")
            Instant checkinAt,
            @Schema(description = "Origem do registro.", example = "manual")
            String source
    ) {}

    @Schema(description = "Dados para checkout por sessao de presenca.")
    public record CheckoutRequest(
            @Schema(description = "Identificador da presenca aberta.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6")
            UUID presencaId,
            @Schema(description = "Instante do checkout. Se omitido, usa o horario atual.", example = "2026-04-18T21:00:00Z")
            Instant checkoutAt
    ) {}

    @Schema(description = "Dados para checkout da presenca aberta de uma pessoa.")
    public record CheckoutByPessoaRequest(
            @Schema(description = "Pessoa alvo. Para USUARIO comum, o backend ignora este campo e usa o subject do JWT.", example = "ravilon")
            String pessoaId,
            @Schema(description = "Instante do checkout. Se omitido, usa o horario atual.", example = "2026-04-18T21:00:00Z")
            Instant checkoutAt
    ) {}

    @Schema(description = "Presenca registrada em um compartimento.")
    public record PresencaResponse(
            @Schema(description = "Identificador da presenca.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6")
            UUID id,
            @Schema(description = "Identificador da pessoa.", example = "ravilon")
            String pessoaId,
            @Schema(description = "Nome da pessoa.", example = "Ravilon A. Santos")
            String pessoaNome,
            @Schema(description = "Identificador do compartimento.", example = "2")
            String compartimentoId,
            @Schema(description = "Nome do compartimento.", example = "Sala 2")
            String compartimentoNome,
            @Schema(description = "Instante do check-in.", example = "2026-04-18T20:00:00Z")
            Instant checkinAt,
            @Schema(description = "Instante do checkout. Nulo enquanto a presenca estiver aberta.", example = "2026-04-18T21:00:00Z")
            Instant checkoutAt,
            @Schema(description = "Origem do registro.", example = "manual")
            String source,
            @Schema(description = "Instante de criacao do registro.", example = "2026-04-18T20:00:00Z")
            Instant createdAt
    ) {}

    @Schema(description = "Ocupacao atual agregada de um compartimento.")
    public record OcupacaoResponse(
            @Schema(description = "Identificador do compartimento.", example = "2")
            String compartimentoId,
            @Schema(description = "Nome do compartimento.", example = "Sala 2")
            String compartimentoNome,
            @Schema(description = "Quantidade de presencas abertas no compartimento.", example = "3")
            long pessoasPresentes
    ) {}
}
