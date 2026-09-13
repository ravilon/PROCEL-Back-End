package com.procel.api.service.missions.beneficiaries;

import com.procel.api.entity.missions.EventoPoliticaAtribuicao;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class DefaultMissionBeneficiaryResolver implements MissionBeneficiaryResolver {

    @Override
    public MissionBeneficiaryResolution resolve(MissionBeneficiaryContext context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (context.politicaAtribuicao() == null) throw new IllegalArgumentException("politicaAtribuicao is required");

        return switch (context.politicaAtribuicao()) {
            case ALUNOS_VINCULADOS -> resolveAlunosVinculados(context);
            case SEM_ATRIBUICAO_AUTOMATICA -> empty(context.politicaAtribuicao(), "Automatic attribution disabled", true);
            case ATIVADOR_DA_MISSAO -> resolveAtivador(context);
            case ALUNOS_VINCULADOS_COM_OCUPACAO, CHECKIN_CONFIRMADO ->
                    empty(context.politicaAtribuicao(), "Attribution policy not supported in this stage", false);
        };
    }

    private static MissionBeneficiaryResolution resolveAlunosVinculados(MissionBeneficiaryContext context) {
        if (context.academicContext().isEmpty() || context.academicContext().get().empty()) {
            return empty(context.politicaAtribuicao(), "Academic context absent", true);
        }
        List<String> people = new LinkedHashSet<>(context.academicContext().get().pessoaElegivelIds())
                .stream()
                .toList();
        String reason = people.isEmpty() ? "Academic context has no eligible people" : "Eligible academic students";
        return new MissionBeneficiaryResolution(people, context.politicaAtribuicao(), Instant.now(), reason, true);
    }

    private static MissionBeneficiaryResolution resolveAtivador(MissionBeneficiaryContext context) {
        return context.pessoaAtivadoraId()
                .filter(value -> !value.isBlank())
                .map(value -> new MissionBeneficiaryResolution(
                        List.of(value),
                        context.politicaAtribuicao(),
                        Instant.now(),
                        "Mission activator",
                        true))
                .orElseGet(() -> empty(context.politicaAtribuicao(), "Mission activator absent", true));
    }

    private static MissionBeneficiaryResolution empty(
            EventoPoliticaAtribuicao policy,
            String reason,
            boolean supported
    ) {
        return new MissionBeneficiaryResolution(List.of(), policy, Instant.now(), reason, supported);
    }
}
