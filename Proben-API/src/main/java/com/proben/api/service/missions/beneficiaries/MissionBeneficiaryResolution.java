package com.proben.api.service.missions.beneficiaries;

import com.proben.api.entity.missions.EventoPoliticaAtribuicao;

import java.time.Instant;
import java.util.List;

/**
 * Resultado da aplicação de uma política de atribuição de beneficiários.
 *
 * @param pessoaIds identificadores das pessoas selecionadas
 * @param politicaUtilizada política aplicada à ocorrência
 * @param resolvedAt instante em que a resolução foi realizada
 * @param reason justificativa do resultado ou da ausência de beneficiários
 * @param supported indica se a política foi reconhecida e aplicada
 */

public record MissionBeneficiaryResolution(
        List<String> pessoaIds,
        EventoPoliticaAtribuicao politicaUtilizada,
        Instant resolvedAt,
        String reason,
        boolean supported
) {
    public MissionBeneficiaryResolution {
        pessoaIds = List.copyOf(pessoaIds);
    }
}
