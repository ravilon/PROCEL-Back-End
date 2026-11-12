package com.proben.api.service.missions.beneficiaries;

import com.proben.api.entity.missions.EventoOcorrencia;
import com.proben.api.entity.missions.EventoPoliticaAtribuicao;
import com.proben.api.service.academic.AcademicContext;

import java.util.Optional;

/**
 * Componente de mission beneficiary context usado pelo modulo API.
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
