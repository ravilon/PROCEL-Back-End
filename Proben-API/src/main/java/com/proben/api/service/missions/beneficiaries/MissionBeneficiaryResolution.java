package com.proben.api.service.missions.beneficiaries;

import com.proben.api.entity.missions.EventoPoliticaAtribuicao;

import java.time.Instant;
import java.util.List;

/**
 * Componente de mission beneficiary resolution usado pelo modulo API.
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
