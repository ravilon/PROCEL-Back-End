package com.proben.api.repository.missions;

import com.proben.api.entity.missions.PontosLancamento;
import com.proben.api.entity.missions.PontosLancamentoTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de acesso e consulta aos dados de pontos lancamento.
 */

public interface PontosLancamentoRepository extends Repository<PontosLancamento, UUID> {
    PontosLancamento save(PontosLancamento lancamento);
    Optional<PontosLancamento> findById(UUID id);
    Optional<PontosLancamento> findByChaveIdempotencia(String chaveIdempotencia);
    Optional<PontosLancamento> findFirstByAtividadeIdAndTipoAndCreatedBy(
            UUID atividadeId,
            PontosLancamentoTipo tipo,
            String createdBy
    );
    Page<PontosLancamento> findByPessoaId(String pessoaId, Pageable pageable);
    Page<PontosLancamento> findByPessoaIdAndTipo(String pessoaId, PontosLancamentoTipo tipo, Pageable pageable);
}
