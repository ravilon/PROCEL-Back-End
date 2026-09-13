package com.procel.api.service.missions.beneficiaries;

import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoPoliticaAtribuicao;
import com.procel.api.service.academic.AcademicContext;

import java.util.Optional;

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
