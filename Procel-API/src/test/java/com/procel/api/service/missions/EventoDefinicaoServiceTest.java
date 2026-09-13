package com.procel.api.service.missions;

import com.procel.api.dto.missions.EventoDTOs;
import com.procel.api.entity.missions.EventoAgregacao;
import com.procel.api.entity.missions.EventoCondicao;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOperadorLogico;
import com.procel.api.entity.missions.EventoPoliticaAtribuicao;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.entity.missions.Missao;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.RegraOperador;
import com.procel.api.entity.sensors.TipoDeSensor;
import com.procel.api.exception.ConflictException;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.missions.EventoCondicaoRepository;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.repository.missions.MissaoRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventoDefinicaoServiceTest {

    private final EventoDefinicaoRepository eventoRepo = mock(EventoDefinicaoRepository.class);
    private final EventoCondicaoRepository condicaoRepo = mock(EventoCondicaoRepository.class);
    private final MissaoRepository missaoRepo = mock(MissaoRepository.class);
    private final ParametroDefRepository parametroRepo = mock(ParametroDefRepository.class);
    private final EventoDefinicaoService service = new EventoDefinicaoService(
            eventoRepo,
            condicaoRepo,
            missaoRepo,
            parametroRepo
    );

    @Test
    void createsEventForExistingMission() {
        UUID missaoId = UUID.randomUUID();
        Missao missao = missao(missaoId, true);
        when(missaoRepo.findById(missaoId)).thenReturn(Optional.of(missao));
        when(eventoRepo.save(any(EventoDefinicao.class))).thenAnswer(invocation -> {
            EventoDefinicao evento = invocation.getArgument(0);
            ReflectionTestUtils.setField(evento, "id", UUID.randomUUID());
            return evento;
        });
        when(condicaoRepo.findByEventoDefinicaoIdAndAtivoTrueOrderByOrdemAscCreatedAtAsc(any())).thenReturn(List.of());

        var response = service.criarEvento(missaoId, eventRequest("Evento", true, 2));

        assertThat(response.missaoId()).isEqualTo(missaoId);
        assertThat(response.nome()).isEqualTo("Evento");
        assertThat(response.quantidadeNecessaria()).isEqualTo(2);
        assertThat(response.ativo()).isTrue();
    }

    @Test
    void rejectsMissingMission() {
        UUID missaoId = UUID.randomUUID();
        when(missaoRepo.findById(missaoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarEvento(missaoId, eventRequest("Evento", true, 1)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsEventsInRepositoryOrder() {
        UUID missaoId = UUID.randomUUID();
        when(missaoRepo.existsById(missaoId)).thenReturn(true);
        EventoDefinicao second = evento(UUID.randomUUID(), missao(missaoId, true), "Segundo", 2, true);
        EventoDefinicao first = evento(UUID.randomUUID(), missao(missaoId, true), "Primeiro", 1, true);
        when(eventoRepo.findByMissaoIdOrderByOrdemAscCreatedAtAsc(missaoId)).thenReturn(List.of(first, second));
        when(condicaoRepo.findByEventoDefinicaoIdAndAtivoTrueOrderByOrdemAscCreatedAtAsc(any())).thenReturn(List.of());

        var response = service.listarEventosDaMissao(missaoId);

        assertThat(response).extracting(EventoDTOs.EventoDefinicaoResponse::nome)
                .containsExactly("Primeiro", "Segundo");
    }

    @Test
    void updatesEvent() {
        UUID eventoId = UUID.randomUUID();
        EventoDefinicao evento = evento(eventoId, missao(UUID.randomUUID(), true), "Antigo", 1, true);
        when(eventoRepo.findById(eventoId)).thenReturn(Optional.of(evento));
        when(condicaoRepo.findByEventoDefinicaoIdAndAtivoTrueOrderByOrdemAscCreatedAtAsc(eventoId)).thenReturn(List.of());

        var response = service.atualizarEvento(eventoId, eventRequest("Novo", false, 3));

        assertThat(response.nome()).isEqualTo("Novo");
        assertThat(response.ativo()).isFalse();
        assertThat(response.quantidadeNecessaria()).isEqualTo(3);
    }

    @Test
    void logicalDeleteOnlyDeactivatesEvent() {
        UUID eventoId = UUID.randomUUID();
        EventoDefinicao evento = evento(eventoId, missao(UUID.randomUUID(), true), "Evento", 1, true);
        when(eventoRepo.findById(eventoId)).thenReturn(Optional.of(evento));

        service.removerEvento(eventoId);

        assertThat(evento.isAtivo()).isFalse();
    }

    @Test
    void createsNumericBooleanAndTextConditions() {
        UUID eventoId = UUID.randomUUID();
        EventoDefinicao evento = evento(eventoId, missao(UUID.randomUUID(), true), "Evento", 1, true);
        ParametroDef numeric = parametro(UUID.randomUUID(), "temperature", DataType.NUMERIC);
        ParametroDef bool = parametro(UUID.randomUUID(), "presence", DataType.BOOLEAN);
        ParametroDef text = parametro(UUID.randomUUID(), "state", DataType.TEXT);
        when(eventoRepo.findById(eventoId)).thenReturn(Optional.of(evento));
        when(parametroRepo.findById(numeric.getId())).thenReturn(Optional.of(numeric));
        when(parametroRepo.findById(bool.getId())).thenReturn(Optional.of(bool));
        when(parametroRepo.findById(text.getId())).thenReturn(Optional.of(text));
        when(condicaoRepo.save(any(EventoCondicao.class))).thenAnswer(invocation -> {
            EventoCondicao condicao = invocation.getArgument(0);
            ReflectionTestUtils.setField(condicao, "id", UUID.randomUUID());
            return condicao;
        });

        var numericResponse = service.criarCondicao(eventoId, conditionRequest(
                numeric.getId(), RegraOperador.GT, BigDecimal.TEN, null, null, null, 1));
        var boolResponse = service.criarCondicao(eventoId, conditionRequest(
                bool.getId(), RegraOperador.EQ, null, null, true, null, 2));
        var textResponse = service.criarCondicao(eventoId, conditionRequest(
                text.getId(), RegraOperador.CONTAINS, null, null, null, "ok", 3));

        assertThat(numericResponse.parametroDataType()).isEqualTo(DataType.NUMERIC);
        assertThat(boolResponse.valorBoolean()).isTrue();
        assertThat(textResponse.valorText()).isEqualTo("ok");
    }

    @Test
    void rejectsIncompatibleType() {
        UUID eventoId = UUID.randomUUID();
        ParametroDef bool = parametro(UUID.randomUUID(), "presence", DataType.BOOLEAN);
        when(eventoRepo.findById(eventoId)).thenReturn(Optional.of(
                evento(eventoId, missao(UUID.randomUUID(), true), "Evento", 1, true)));
        when(parametroRepo.findById(bool.getId())).thenReturn(Optional.of(bool));

        assertThatThrownBy(() -> service.criarCondicao(eventoId, conditionRequest(
                bool.getId(), RegraOperador.GT, BigDecimal.ONE, null, null, null, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only EQ and NEQ");
    }

    @Test
    void rejectsBetweenWithoutTwoLimitsAndInvertedLimits() {
        UUID eventoId = UUID.randomUUID();
        ParametroDef numeric = parametro(UUID.randomUUID(), "temperature", DataType.NUMERIC);
        when(eventoRepo.findById(eventoId)).thenReturn(Optional.of(
                evento(eventoId, missao(UUID.randomUUID(), true), "Evento", 1, true)));
        when(parametroRepo.findById(numeric.getId())).thenReturn(Optional.of(numeric));

        assertThatThrownBy(() -> service.criarCondicao(eventoId, conditionRequest(
                numeric.getId(), RegraOperador.BETWEEN, BigDecimal.ONE, null, null, null, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valorNumeric1 and valorNumeric2");

        assertThatThrownBy(() -> service.criarCondicao(eventoId, conditionRequest(
                numeric.getId(), RegraOperador.BETWEEN, BigDecimal.TEN, BigDecimal.ONE, null, null, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valorNumeric2");
    }

    @Test
    void rejectsDuplicateOrder() {
        UUID eventoId = UUID.randomUUID();
        ParametroDef numeric = parametro(UUID.randomUUID(), "temperature", DataType.NUMERIC);
        when(eventoRepo.findById(eventoId)).thenReturn(Optional.of(
                evento(eventoId, missao(UUID.randomUUID(), true), "Evento", 1, true)));
        when(condicaoRepo.existsByEventoDefinicaoIdAndAtivoTrueAndOrdem(eventoId, 1)).thenReturn(true);

        assertThatThrownBy(() -> service.criarCondicao(eventoId, conditionRequest(
                numeric.getId(), RegraOperador.GT, BigDecimal.ONE, null, null, null, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ordem=1");
    }

    @Test
    void rejectsActivationForInactiveMission() {
        UUID missaoId = UUID.randomUUID();
        when(missaoRepo.findById(missaoId)).thenReturn(Optional.of(missao(missaoId, false)));

        assertThatThrownBy(() -> service.criarEvento(missaoId, eventRequest("Evento", true, 1)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("inactive mission");
    }

    @Test
    void logicalDeleteOnlyDeactivatesCondition() {
        UUID eventoId = UUID.randomUUID();
        UUID condicaoId = UUID.randomUUID();
        EventoCondicao condicao = new EventoCondicao();
        ReflectionTestUtils.setField(condicao, "id", condicaoId);
        condicao.setEventoDefinicao(evento(eventoId, missao(UUID.randomUUID(), true), "Evento", 1, true));
        when(condicaoRepo.findById(condicaoId)).thenReturn(Optional.of(condicao));

        service.removerCondicao(eventoId, condicaoId);

        assertThat(condicao.isAtivo()).isFalse();
    }

    private static EventoDTOs.EventoDefinicaoRequest eventRequest(
            String nome,
            Boolean ativo,
            Integer quantidade
    ) {
        return new EventoDTOs.EventoDefinicaoRequest(
                nome,
                "Descricao",
                EventoTipoDisparo.MEDICAO_RECEBIDA,
                EventoModoAvaliacao.INSTANTANEO,
                EventoOperadorLogico.ALL,
                EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA,
                60,
                null,
                quantidade,
                30,
                1,
                ativo
        );
    }

    private static EventoDTOs.EventoCondicaoRequest conditionRequest(
            UUID parametroDefId,
            RegraOperador operador,
            BigDecimal valorNumeric1,
            BigDecimal valorNumeric2,
            Boolean valorBoolean,
            String valorText,
            Integer ordem
    ) {
        return new EventoDTOs.EventoCondicaoRequest(
                parametroDefId,
                operador,
                valorNumeric1,
                valorNumeric2,
                valorBoolean,
                valorText,
                EventoAgregacao.ULTIMO,
                true,
                ordem
        );
    }

    private static Missao missao(UUID id, boolean ativo) {
        Missao missao = new Missao("Missao", "Descricao", "Individual", 10, ativo);
        ReflectionTestUtils.setField(missao, "id", id);
        return missao;
    }

    private static EventoDefinicao evento(
            UUID id,
            Missao missao,
            String nome,
            Integer ordem,
            boolean ativo
    ) {
        EventoDefinicao evento = new EventoDefinicao();
        ReflectionTestUtils.setField(evento, "id", id);
        evento.setMissao(missao);
        evento.setNome(nome);
        evento.setDescricao("Descricao");
        evento.setTipoDisparo(EventoTipoDisparo.MEDICAO_RECEBIDA);
        evento.setModoAvaliacao(EventoModoAvaliacao.INSTANTANEO);
        evento.setOperadorLogico(EventoOperadorLogico.ALL);
        evento.setPoliticaAtribuicao(EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA);
        evento.setQuantidadeNecessaria(1);
        evento.setOrdem(ordem);
        evento.setAtivo(ativo);
        return evento;
    }

    private static ParametroDef parametro(UUID id, String nome, DataType dataType) {
        ParametroDef parametro = new ParametroDef(new TipoDeSensor("SII_SMART"), nome, null, dataType, null);
        ReflectionTestUtils.setField(parametro, "id", id);
        return parametro;
    }
}
