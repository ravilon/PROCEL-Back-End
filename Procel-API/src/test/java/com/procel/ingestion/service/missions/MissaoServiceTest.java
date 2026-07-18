package com.procel.ingestion.service.missions;

import com.procel.ingestion.dto.missions.MissaoDTOs;
import com.procel.ingestion.entity.missions.Atividade;
import com.procel.ingestion.entity.missions.AtividadeStatus;
import com.procel.ingestion.entity.missions.Missao;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.repository.missions.AtividadeRepository;
import com.procel.ingestion.repository.missions.MissaoRepository;
import com.procel.ingestion.repository.people.PessoaRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissaoServiceTest {

    @Test
    void assigningParentCreatesChildActivitiesAndReportsProgress() {
        MissaoRepository missaoRepo = mock(MissaoRepository.class);
        AtividadeRepository atividadeRepo = mock(AtividadeRepository.class);
        PessoaRepository pessoaRepo = mock(PessoaRepository.class);
        MissaoService service = new MissaoService(missaoRepo, atividadeRepo, pessoaRepo);

        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Missao parent = mission(parentId, "Missao pai", null);
        Missao child = mission(childId, "Etapa filha", parent);
        Pessoa pessoa = new Pessoa("p1", "Pessoa", "p1@example.com", "hash", null, null);
        Map<UUID, Atividade> activities = new HashMap<>();

        when(pessoaRepo.findById("p1")).thenReturn(Optional.of(pessoa));
        when(missaoRepo.findById(parentId)).thenReturn(Optional.of(parent));
        when(missaoRepo.findByParent_IdOrderByCreatedAtAsc(parentId)).thenReturn(List.of(child));
        when(missaoRepo.findByParent_IdOrderByCreatedAtAsc(childId)).thenReturn(List.of());
        when(atividadeRepo.existsByPessoaIdAndMissaoId("p1", parentId)).thenReturn(false);
        when(atividadeRepo.findByPessoaIdAndMissaoId(any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(activities.get(invocation.getArgument(1))));
        when(atividadeRepo.save(any(Atividade.class))).thenAnswer(invocation -> {
            Atividade activity = invocation.getArgument(0);
            activities.put(activity.getMissao().getId(), activity);
            return activity;
        });

        var response = service.atribuir(
                "p1",
                new MissaoDTOs.AtribuirMissaoRequest(parentId, AtividadeStatus.PENDENTE, null)
        );

        assertThat(activities).containsKeys(parentId, childId);
        assertThat(response.totalFilhas()).isEqualTo(1);
        assertThat(response.filhasConcluidas()).isZero();
        assertThat(response.progressoPercentual()).isZero();
    }

    private static Missao mission(UUID id, String title, Missao parent) {
        Missao mission = mock(Missao.class);
        when(mission.getId()).thenReturn(id);
        when(mission.getTitulo()).thenReturn(title);
        when(mission.getDescricao()).thenReturn("");
        when(mission.getTipo()).thenReturn("Individual");
        when(mission.getValue()).thenReturn(10);
        when(mission.isAtivo()).thenReturn(true);
        when(mission.getParent()).thenReturn(parent);
        return mission;
    }
}
