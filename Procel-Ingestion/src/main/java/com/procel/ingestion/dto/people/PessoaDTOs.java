package com.procel.ingestion.dto.people;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;

public class PessoaDTOs {

    @Schema(description = "Dados para criacao de pessoa. Requer role ADMIN.")
    public record CreatePessoaRequest(
            @Schema(description = "Nome completo.", example = "Ravilon Aguiar")
            String nome,
            @Schema(description = "E-mail unico da pessoa.", example = "ravilon@exemplo.com")
            String email,
            @Schema(description = "Identificador unico usado como PK e subject do JWT.", example = "ravilon")
            String userId,
            @Schema(description = "Senha inicial. Sera armazenada como hash BCrypt.", example = "123456")
            String password,
            @Schema(description = "Telefone de contato.", example = "51999999999")
            String telefone,
            @Schema(description = "Matricula academica, quando aplicavel.", example = "MAT-001")
            String matricula,
            @Schema(description = "Roles globais da pessoa. Se omitido, assume USUARIO.", example = "[\"USUARIO\"]")
            Set<String> roles
    ) {}

    @Schema(description = "Dados para atualizacao parcial de pessoa.")
    public record UpdatePessoaRequest(
            @Schema(description = "Nome completo.", example = "Ravilon A. Santos")
            String nome,
            @Schema(description = "E-mail unico da pessoa.", example = "ravilon@exemplo.com")
            String email,
            @Schema(description = "Nao pode ser alterado; se informado deve ser igual ao id da URL.", example = "ravilon")
            String userId,
            @Schema(description = "Nova senha. Sera armazenada como hash BCrypt.", example = "123456")
            String password,
            @Schema(description = "Telefone de contato.", example = "51988887777")
            String telefone,
            @Schema(description = "Matricula academica, quando aplicavel.", example = "MAT-001")
            String matricula,
            @Schema(description = "Roles globais. Apenas ADMIN pode alterar roles.", example = "[\"USUARIO\"]")
            Set<String> roles
    ) {}

    @Schema(description = "Pessoa retornada pela API. A senha nunca e retornada.")
    public record PessoaResponse(
            @Schema(description = "Identificador unico da pessoa.", example = "ravilon")
            String id,
            @Schema(description = "Nome completo.", example = "Ravilon A. Santos")
            String nome,
            @Schema(description = "E-mail da pessoa.", example = "ravilon@exemplo.com")
            String email,
            @Schema(description = "Telefone de contato.", example = "51988887777")
            String telefone,
            @Schema(description = "Matricula academica.", example = "MAT-001")
            String matricula,
            @Schema(description = "Data de criacao.", example = "2026-04-18T20:00:00Z")
            Instant createdAt,
            @Schema(description = "Roles globais da pessoa.", example = "[\"USUARIO\"]")
            Set<String> roles
    ) {}
}
