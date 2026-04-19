package com.procel.ingestion.dto.people;

import java.time.Instant;
import java.util.Set;

public class PessoaDTOs {

    public record CreatePessoaRequest(
            String nome,
            String email,
            String userId,
            String password,
            String telefone,
            String matricula,
            Set<String> roles
    ) {}

    public record UpdatePessoaRequest(
            String nome,
            String email,
            String userId,
            String password,
            String telefone,
            String matricula,
            Set<String> roles
    ) {}

    public record PessoaResponse(
            String id,
            String nome,
            String email,
            String telefone,
            String matricula,
            Instant createdAt,
            Set<String> roles
    ) {}
}
