package com.procel.api.service.academic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AcademicContext(
        UUID periodoAulaId,
        Long disciplinaId,
        String turma,
        String periodoLetivo,
        String compartimentoId,
        LocalDateTime inicio,
        LocalDateTime fim,
        List<String> pessoaElegivelIds
) {
    public AcademicContext {
        pessoaElegivelIds = List.copyOf(pessoaElegivelIds);
    }

    public static AcademicContext empty(String compartimentoId) {
        return new AcademicContext(
                null,
                null,
                null,
                null,
                compartimentoId,
                null,
                null,
                List.of()
        );
    }

    public boolean empty() {
        return inicio == null && fim == null;
    }
}
