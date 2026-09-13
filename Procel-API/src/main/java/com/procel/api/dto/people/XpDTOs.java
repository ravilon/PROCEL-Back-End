package com.procel.api.dto.people;

import com.procel.api.entity.missions.XpLancamentoTipo;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class XpDTOs {
    private XpDTOs() {}

    public record XpSaldoResponse(
            String pessoaId,
            long saldoTotal,
            long totalConcedido,
            long totalEstornado,
            long totalAjustado
    ) {}

    public record XpLancamentoResponse(
            UUID id,
            UUID atividadeId,
            UUID missaoId,
            String missaoTitulo,
            UUID eventoOcorrenciaId,
            XpLancamentoTipo tipo,
            int quantidade,
            String descricao,
            Instant createdAt
    ) {}

    public record XpLancamentosPageResponse(
            List<XpLancamentoResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static XpLancamentosPageResponse from(Page<XpLancamentoResponse> page) {
            return new XpLancamentosPageResponse(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
