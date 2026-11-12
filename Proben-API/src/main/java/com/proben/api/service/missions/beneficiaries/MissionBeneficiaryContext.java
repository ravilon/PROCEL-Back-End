package com.proben.api.service.missions.beneficiaries;

import com.proben.api.entity.missions.EventoOcorrencia;
import com.proben.api.entity.missions.EventoPoliticaAtribuicao;
import com.proben.api.service.academic.AcademicContext;

import java.util.Optional;

/**
 * Contexto usado para resolver as pessoas beneficiárias de uma ocorrência.
 *
 * @param politicaAtribuicao política que define quem pode receber crédito
 * @param eventoOcorrencia ocorrência que originou a avaliação
 * @param academicContext contexto acadêmico disponível para a ocorrência
 * @param pessoaAtivadoraId pessoa que iniciou a ação, quando identificada
 * @param ocupacaoFisicaConfirmada indica se a ocupação física foi confirmada
 */

public record MissionBeneficiaryContext(
        EventoPoliticaAtribuicao politicaAtribuicao,
        EventoOcorrencia eventoOcorrencia,
        Optional<AcademicContext> academicContext,
        Optional<String> pessoaAtivadoraId,
        Optional<Boolean> ocupacaoFisicaConfirmada
) {
    public MissionBeneficiaryContext {
        academicContext = academicContext == null ? Optional.empty() : academicContext;
        pessoaAtivadoraId = pessoaAtivadoraId == null ? Optional.empty() : pessoaAtivadoraId;
        ocupacaoFisicaConfirmada = ocupacaoFisicaConfirmada == null ? Optional.empty() : ocupacaoFisicaConfirmada;
    }
}
