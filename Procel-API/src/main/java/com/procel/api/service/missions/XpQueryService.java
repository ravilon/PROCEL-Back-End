package com.procel.api.service.missions;

import com.procel.api.dto.people.XpDTOs;
import com.procel.api.entity.missions.XpLancamento;
import com.procel.api.entity.missions.XpLancamentoTipo;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.missions.XpLancamentoRepository;
import com.procel.api.repository.people.PessoaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class XpQueryService {

    private final PessoaRepository pessoaRepository;
    private final XpLancamentoRepository xpLancamentoRepository;
    private final JdbcTemplate jdbcTemplate;

    public XpQueryService(
            PessoaRepository pessoaRepository,
            XpLancamentoRepository xpLancamentoRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.pessoaRepository = pessoaRepository;
        this.xpLancamentoRepository = xpLancamentoRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public XpDTOs.XpSaldoResponse saldo(String pessoaId) {
        ensurePessoaExists(pessoaId);
        return jdbcTemplate.queryForObject("""
                select
                    coalesce(sum(quantidade), 0) as saldo_total,
                    coalesce(sum(case when tipo = 'CONCESSAO' then quantidade else 0 end), 0) as total_concedido,
                    coalesce(sum(case when tipo = 'ESTORNO' then quantidade else 0 end), 0) as total_estornado,
                    coalesce(sum(case when tipo = 'AJUSTE' then quantidade else 0 end), 0) as total_ajustado
                from xp_lancamento
                where pessoa_id = ?
                """,
                (rs, rowNum) -> new XpDTOs.XpSaldoResponse(
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
    public XpDTOs.XpLancamentosPageResponse lancamentos(String pessoaId, XpLancamentoTipo tipo, int page, int size) {
        ensurePessoaExists(pessoaId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        var pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<XpLancamento> result = tipo == null
                ? xpLancamentoRepository.findByPessoaId(pessoaId, pageable)
                : xpLancamentoRepository.findByPessoaIdAndTipo(pessoaId, tipo, pageable);
        return XpDTOs.XpLancamentosPageResponse.from(result.map(this::toResponse));
    }

    private void ensurePessoaExists(String pessoaId) {
        if (!pessoaRepository.existsById(pessoaId)) {
            throw new NotFoundException("Pessoa not found id=" + pessoaId);
        }
    }

    private XpDTOs.XpLancamentoResponse toResponse(XpLancamento lancamento) {
        return new XpDTOs.XpLancamentoResponse(
                lancamento.getId(),
                lancamento.getAtividade().getId(),
                lancamento.getMissao().getId(),
                lancamento.getMissao().getTitulo(),
                lancamento.getEventoOcorrencia() == null ? null : lancamento.getEventoOcorrencia().getId(),
                lancamento.getTipo(),
                lancamento.getQuantidade(),
                lancamento.getDescricao(),
                lancamento.getCreatedAt()
        );
    }
}
