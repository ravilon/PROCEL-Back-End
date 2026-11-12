package com.proben.api.service.missions;

import com.proben.api.dto.people.PontosDTOs;
import com.proben.api.entity.missions.PontosLancamento;
import com.proben.api.entity.missions.PontosLancamentoTipo;
import com.proben.api.exception.NotFoundException;
import com.proben.api.repository.missions.PontosLancamentoRepository;
import com.proben.api.repository.people.PessoaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servico que implementa as operacoes de negocio de pontos query.
 */
@Service
public class PontosQueryService {

    private final PessoaRepository pessoaRepository;
    private final PontosLancamentoRepository pontosLancamentoRepository;
    private final JdbcTemplate jdbcTemplate;

    public PontosQueryService(
            PessoaRepository pessoaRepository,
            PontosLancamentoRepository pontosLancamentoRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.pessoaRepository = pessoaRepository;
        this.pontosLancamentoRepository = pontosLancamentoRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PontosDTOs.PontosSaldoResponse saldo(String pessoaId) {
        ensurePessoaExists(pessoaId);
        return jdbcTemplate.queryForObject("""
                select
                    coalesce(sum(pontos), 0) as saldo_total,
                    coalesce(sum(case when tipo = 'CONCESSAO' then pontos else 0 end), 0) as total_concedido,
                    coalesce(sum(case when tipo = 'ESTORNO' then pontos else 0 end), 0) as total_estornado,
                    coalesce(sum(case when tipo = 'AJUSTE' then pontos else 0 end), 0) as total_ajustado
                from pontos_lancamento
                where pessoa_id = ?
                """,
                (rs, rowNum) -> new PontosDTOs.PontosSaldoResponse(
                        pessoaId,
                        rs.getLong("saldo_total"),
                        rs.getLong("total_concedido"),
                        rs.getLong("total_estornado"),
                        rs.getLong("total_ajustado")
                ),
                pessoaId
        );
    }

    @Transactional(readOnly = true)
    public PontosDTOs.PontosLancamentosPageResponse lancamentos(String pessoaId, PontosLancamentoTipo tipo, int page, int size) {
        ensurePessoaExists(pessoaId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        var pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<PontosLancamento> result = tipo == null
                ? pontosLancamentoRepository.findByPessoaId(pessoaId, pageable)
                : pontosLancamentoRepository.findByPessoaIdAndTipo(pessoaId, tipo, pageable);
        return PontosDTOs.PontosLancamentosPageResponse.from(result.map(this::toResponse));
    }

    private void ensurePessoaExists(String pessoaId) {
        if (!pessoaRepository.existsById(pessoaId)) {
            throw new NotFoundException("Pessoa not found id=" + pessoaId);
        }
    }

    private PontosDTOs.PontosLancamentoResponse toResponse(PontosLancamento lancamento) {
        return new PontosDTOs.PontosLancamentoResponse(
                lancamento.getId(),
                lancamento.getAtividade().getId(),
                lancamento.getMissao().getId(),
                lancamento.getMissao().getTitulo(),
                lancamento.getEventoOcorrencia() == null ? null : lancamento.getEventoOcorrencia().getId(),
                lancamento.getTipo(),
                lancamento.getPontos(),
                lancamento.getDescricao(),
                lancamento.getCreatedAt()
        );
    }
}
