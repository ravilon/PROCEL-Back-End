package com.procel.api.service.missions.beneficiaries;

import com.procel.api.entity.missions.EventoPoliticaAtribuicao;

import java.time.Instant;
import java.util.List;

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
