package com.procel.api.service.missions.beneficiaries;

import com.procel.api.entity.missions.EventoPoliticaAtribuicao;
import com.procel.api.service.academic.AcademicContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMissionBeneficiaryResolverTest {

    private final DefaultMissionBeneficiaryResolver resolver = new DefaultMissionBeneficiaryResolver();

    @Test
    void linkedStudentsReturnsEligiblePeopleWithoutDuplicates() {
        AcademicContext academicContext = new AcademicContext(
                UUID.randomUUID(),
                10L,
                "A",
                "2026/1",
                "ROOM",
                LocalDateTime.parse("2026-09-13T10:00:00"),
                LocalDateTime.parse("2026-09-13T10:50:00"),
                List.of("p1", "p2", "p1")
        );

        var resolution = resolver.resolve(context(EventoPoliticaAtribuicao.ALUNOS_VINCULADOS,
                Optional.of(academicContext), Optional.empty()));

        assertThat(resolution.supported()).isTrue();
        assertThat(resolution.pessoaIds()).containsExactly("p1", "p2");
    }

    @Test
    void absentAcademicContextReturnsEmptyList() {
        var resolution = resolver.resolve(context(EventoPoliticaAtribuicao.ALUNOS_VINCULADOS,
                Optional.empty(), Optional.empty()));

        assertThat(resolution.pessoaIds()).isEmpty();
        assertThat(resolution.reason()).contains("Academic context absent");
    }

    @Test
    void activatorPolicyReturnsActivatorWhenPresent() {
        var resolution = resolver.resolve(context(EventoPoliticaAtribuicao.ATIVADOR_DA_MISSAO,
                Optional.empty(), Optional.of("p1")));

        assertThat(resolution.pessoaIds()).containsExactly("p1");
    }

    @Test
    void noAutomaticAttributionReturnsEmptyList() {
        var resolution = resolver.resolve(context(EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA,
                Optional.empty(), Optional.of("p1")));

        assertThat(resolution.pessoaIds()).isEmpty();
        assertThat(resolution.supported()).isTrue();
    }

    @Test
    void unsupportedPoliciesAreExplicit() {
        var occupancy = resolver.resolve(context(EventoPoliticaAtribuicao.ALUNOS_VINCULADOS_COM_OCUPACAO,
                Optional.empty(), Optional.empty()));
        var checkin = resolver.resolve(context(EventoPoliticaAtribuicao.CHECKIN_CONFIRMADO,
                Optional.empty(), Optional.empty()));

        assertThat(occupancy.supported()).isFalse();
        assertThat(checkin.supported()).isFalse();
        assertThat(occupancy.reason()).contains("not supported");
        assertThat(checkin.reason()).contains("not supported");
    }

    private static MissionBeneficiaryContext context(
            EventoPoliticaAtribuicao policy,
            Optional<AcademicContext> academicContext,
            Optional<String> activator
    ) {
        return new MissionBeneficiaryContext(policy, null, academicContext, activator, Optional.empty());
    }
}
