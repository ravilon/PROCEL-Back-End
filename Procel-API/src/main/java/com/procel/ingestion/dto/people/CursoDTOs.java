package com.procel.ingestion.dto.people;

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
