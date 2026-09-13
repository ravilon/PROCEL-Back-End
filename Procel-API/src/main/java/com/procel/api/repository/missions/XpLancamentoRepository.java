package com.procel.api.repository.missions;

import com.procel.api.entity.missions.XpLancamento;
import com.procel.api.entity.missions.XpLancamentoTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface XpLancamentoRepository extends Repository<XpLancamento, UUID> {
    XpLancamento save(XpLancamento lancamento);
    Optional<XpLancamento> findById(UUID id);
    Optional<XpLancamento> findByChaveIdempotencia(String chaveIdempotencia);
    Optional<XpLancamento> findFirstByAtividadeIdAndTipoAndCreatedBy(
            UUID atividadeId,
            XpLancamentoTipo tipo,
            String createdBy
    );
    Page<XpLancamento> findByPessoaId(String pessoaId, Pageable pageable);
    Page<XpLancamento> findByPessoaIdAndTipo(String pessoaId, XpLancamentoTipo tipo, Pageable pageable);
}
