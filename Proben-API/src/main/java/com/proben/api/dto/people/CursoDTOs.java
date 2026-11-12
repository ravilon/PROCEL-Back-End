package com.proben.api.dto.people;

/**
 * Definicoes dos objetos de transferencia de dados de curso.
 */

public final class CursoDTOs {

    private CursoDTOs() {}

    public record CursoRequest(
            String nome,
            String unidadeSigla
    ) {}

    public record CursoResponse(
            Long id,
            String nome,
            String unidadeSigla
    ) {}

    public record PessoaCursoResponse(
            String pessoaId,
            String pessoaNome,
            CursoResponse curso
    ) {}
}
