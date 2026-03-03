package com.procel.ingestion.dto.people;

import java.time.Instant;

public class PessoaDTOs {

    public record CreatePessoaRequest(
            String nome,
            String email,
            String userId,
            String password,
            String telefone,
            String matricula
    ) {}

    public record UpdatePessoaRequest(
            String nome,
            String email,
            String userId,   // NÃO será permitido mudar (PK)
            String password,
            String telefone,
            String matricula
    ) {}

    public record PessoaResponse(
            String id,
            String nome,
            String email,
            String telefone,
            String matricula,
            Instant createdAt
    ) {}
}