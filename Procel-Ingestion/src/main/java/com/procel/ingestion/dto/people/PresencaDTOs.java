package com.procel.ingestion.dto.people;

import java.time.Instant;
import java.util.UUID;

public class PresencaDTOs {

    public record CheckinRequest(
            String pessoaId,
            String compartimentoId,
            Instant checkinAt,
            String source
    ) {}

    public record CheckoutRequest(
            UUID presencaId,
            Instant checkoutAt
    ) {}

    public record PresencaResponse(
            UUID id,
            String pessoaId,
            String pessoaNome,
            String compartimentoId,
            String compartimentoNome,
            Instant checkinAt,
            Instant checkoutAt,
            String source,
            Instant createdAt
    ) {}

    public record OcupacaoResponse(
            String compartimentoId,
            String compartimentoNome,
            long pessoasPresentes
    ) {}
}