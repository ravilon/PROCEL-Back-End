package com.procel.api.dto.missions;

import com.procel.api.entity.missions.EventoAgregacao;
import com.procel.api.entity.missions.EventoPapel;
import com.procel.api.entity.missions.EventoCondicaoFonte;
import com.procel.api.entity.sensors.AvaliacaoResultado;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOperadorLogico;
import com.procel.api.entity.missions.EventoPoliticaAtribuicao;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.RegraOperador;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventoDTOs {
    private EventoDTOs() {}

    @Schema(description = "Dados para criar ou atualizar uma definicao de evento de missao.")
    public record EventoDefinicaoRequest(
            @Schema(example = "Sala com temperatura critica")
            String nome,
            String descricao,
            EventoTipoDisparo tipoDisparo,
            EventoModoAvaliacao modoAvaliacao,
            EventoOperadorLogico operadorLogico,
            EventoPoliticaAtribuicao politicaAtribuicao,
            @Schema(description = "Janela de avaliacao em segundos. Nulo quando nao aplicavel.")
            Integer janelaSegundos,
            @Schema(description = "Duracao minima em segundos. Nulo quando nao aplicavel.")
            Integer duracaoMinimaSegundos,
            @Schema(description = "Quantidade necessaria de ocorrencias. Minimo 1.")
            Integer quantidadeNecessaria,
            @Schema(description = "Cooldown em segundos. Nulo quando nao aplicavel.")
            Integer cooldownSegundos,
            Integer ordem,
            Boolean ativo,
            EventoPapel papel,
            Integer lacunaMaximaSegundos
    ) {
        public EventoDefinicaoRequest(String nome, String descricao, EventoTipoDisparo tipoDisparo,
                EventoModoAvaliacao modoAvaliacao, EventoOperadorLogico operadorLogico,
                EventoPoliticaAtribuicao politicaAtribuicao, Integer janelaSegundos,
                Integer duracaoMinimaSegundos, Integer quantidadeNecessaria,
                Integer cooldownSegundos, Integer ordem, Boolean ativo) {
            this(nome, descricao, tipoDisparo, modoAvaliacao, operadorLogico, politicaAtribuicao,
                    janelaSegundos, duracaoMinimaSegundos, quantidadeNecessaria,
                    cooldownSegundos, ordem, ativo, EventoPapel.PROGRESSO, null);
        }
    }

    @Schema(description = "Dados para criar ou atualizar uma condicao de evento.")
    public record EventoCondicaoRequest(
            @Schema(description = "ParametroDef referenciado por FK.")
            UUID parametroDefId,
            RegraOperador operador,
            BigDecimal valorNumeric1,
            BigDecimal valorNumeric2,
            Boolean valorBoolean,
            String valorText,
            EventoAgregacao agregacao,
            Boolean obrigatoria,
            Integer ordem,
            EventoCondicaoFonte fonte,
            UUID regraParametroId,
            AvaliacaoResultado resultadoEsperado,
            Boolean ativo
    ) {
        public EventoCondicaoRequest(UUID parametroDefId, RegraOperador operador,
                BigDecimal valorNumeric1, BigDecimal valorNumeric2, Boolean valorBoolean,
                String valorText, EventoAgregacao agregacao, Boolean obrigatoria, Integer ordem) {
            this(parametroDefId, operador, valorNumeric1, valorNumeric2, valorBoolean,
                    valorText, agregacao, obrigatoria, ordem, EventoCondicaoFonte.PARAMETRO_VALOR, null, null, true);
        }
    }

    @Schema(description = "Definicao de evento de missao com condicoes ordenadas.")
    public record EventoDefinicaoResponse(
            UUID id,
            UUID missaoId,
            String missaoTitulo,
            String nome,
            String descricao,
            EventoTipoDisparo tipoDisparo,
            EventoModoAvaliacao modoAvaliacao,
            EventoOperadorLogico operadorLogico,
            EventoPoliticaAtribuicao politicaAtribuicao,
            Integer janelaSegundos,
            Integer duracaoMinimaSegundos,
            Integer quantidadeNecessaria,
            Integer cooldownSegundos,
            Integer ordem,
            boolean ativo,
            Instant createdAt,
            Instant updatedAt,
            List<EventoCondicaoResponse> condicoes,
            EventoPapel papel,
            Integer lacunaMaximaSegundos
    ) {}

    @Schema(description = "Condicao de evento vinculada a ParametroDef.")
    public record EventoCondicaoResponse(
            UUID id,
            UUID eventoDefinicaoId,
            UUID parametroDefId,
            String parametroNome,
            String parametroTipoSensor,
            DataType parametroDataType,
            RegraOperador operador,
            BigDecimal valorNumeric1,
            BigDecimal valorNumeric2,
            Boolean valorBoolean,
            String valorText,
            EventoAgregacao agregacao,
            boolean obrigatoria,
            Integer ordem,
            boolean ativo,
            Instant createdAt,
            EventoCondicaoFonte fonte,
            UUID regraParametroId,
            String regraNome,
            AvaliacaoResultado resultadoEsperado
    ) {}
}
