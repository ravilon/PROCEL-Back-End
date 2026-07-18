package com.procel.ingestion.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import com.procel.ingestion.dto.people.PessoaDTOs;

import java.time.Instant;
import java.util.Set;

public class AuthDTOs {

    @Schema(description = "Credenciais para autenticacao e emissao de JWT.")
    public record LoginRequest(
            @Schema(description = "E-mail cadastrado da pessoa.", example = "admin@procel.local")
            String email,
            @Schema(description = "Senha em texto claro enviada apenas para login.", example = "admin123")
            String password
    ) {}

    @Schema(description = "Resposta de autenticacao com token JWT.")
    public record LoginResponse(
            @Schema(description = "JWT usado no header Authorization: Bearer <token>.")
            String accessToken,
            @Schema(description = "Tipo do token.", example = "Bearer")
            String tokenType,
            @Schema(description = "Instante de expiracao do token.", example = "2026-04-19T00:00:00Z")
            Instant expiresAt,
            @Schema(description = "Identificador da pessoa autenticada.", example = "admin")
            String userId,
            @Schema(description = "E-mail da pessoa autenticada.", example = "admin@procel.local")
            String email,
            @Schema(description = "Roles globais carregadas no JWT.", example = "[\"ADMIN\",\"OPERADOR\"]")
            Set<String> roles
    ) {}

    @Schema(description = "Dados para auto cadastro publico. Sempre cria usuario com role USUARIO.")
    public record RegisterRequest(
            @Schema(description = "Nome completo.", example = "Aluno Exemplo")
            String nome,
            @Schema(description = "E-mail unico da pessoa.", example = "aluno@exemplo.com")
            String email,
            @Schema(description = "Identificador unico usado como PK e subject do JWT.", example = "aluno123")
            String userId,
            @Schema(description = "Senha inicial. Sera armazenada como hash BCrypt.", example = "123456")
            String password,
            @Schema(description = "Telefone de contato.", example = "51999999999")
            String telefone,
            @Schema(description = "Matricula academica, quando aplicavel.", example = "MAT-123")
            String matricula
    ) {}

    @Schema(description = "Resposta do auto cadastro publico.")
    public record RegisterResponse(
            @Schema(description = "Pessoa criada com role USUARIO.")
            PessoaDTOs.PessoaResponse pessoa
    ) {}
}
