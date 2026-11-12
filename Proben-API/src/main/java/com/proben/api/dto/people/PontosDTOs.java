package com.proben.api.dto.people;

import com.proben.api.entity.missions.PontosLancamentoTipo;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Definicoes dos objetos de transferencia de dados de pontos.
 */

public final class PontosDTOs {
    private PontosDTOs() {}

    public record PontosSaldoResponse(
            String pessoaId,
            long saldoTotalPontos,
            long totalConcedidoPontos,
            long totalEstornadoPontos,
            long totalAjustadoPontos
    ) {}

    public record PontosLancamentoResponse(
            UUID id,
            UUID atividadeId,
            UUID missaoId,
            String missaoTitulo,
            UUID eventoOcorrenciaId,
            PontosLancamentoTipo tipo,
            int pontos,
            String descricao,
            Instant createdAt
    ) {}

    public record PontosLancamentosPageResponse(
            List<PontosLancamentoResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static PontosLancamentosPageResponse from(Page<PontosLancamentoResponse> page) {
            return new PontosLancamentosPageResponse(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
