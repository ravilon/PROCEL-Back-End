package com.procel.api.entity.missions;

import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.RegraParametro;
import com.procel.api.entity.sensors.AvaliacaoResultado;
import com.procel.api.entity.sensors.RegraOperador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "evento_condicao",
        indexes = {
                @Index(name = "ix_evento_condicao_evento_ordem", columnList = "evento_definicao_id,ordem"),
                @Index(name = "ix_evento_condicao_parametro", columnList = "parametro_def_id")
        }
)
public class EventoCondicao {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "evento_definicao_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_condicao_evento")
    )
    private EventoDefinicao eventoDefinicao;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parametro_def_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_condicao_parametro")
    )
    private ParametroDef parametroDef;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte", nullable = false, length = 20)
    private EventoCondicaoFonte fonte = EventoCondicaoFonte.PARAMETRO_VALOR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regra_parametro_id")
    private RegraParametro regraParametro;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado_esperado", length = 30)
    private AvaliacaoResultado resultadoEsperado;

    @Enumerated(EnumType.STRING)
    @Column(name = "operador", nullable = false, length = 30)
    private RegraOperador operador;

    @Column(name = "valor_numeric_1", precision = 18, scale = 6)
    private BigDecimal valorNumeric1;

    @Column(name = "valor_numeric_2", precision = 18, scale = 6)
    private BigDecimal valorNumeric2;

    @Column(name = "valor_boolean")
    private Boolean valorBoolean;

    @Column(name = "valor_text", length = 1000)
    private String valorText;

    @Enumerated(EnumType.STRING)
    @Column(name = "agregacao", nullable = false, length = 40)
    private EventoAgregacao agregacao;

    @Column(name = "obrigatoria", nullable = false)
    private boolean obrigatoria = true;

    @Column(name = "ordem", nullable = false)
    private Integer ordem = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public EventoCondicao() {}

    public UUID getId() { return id; }
    public EventoDefinicao getEventoDefinicao() { return eventoDefinicao; }
    public ParametroDef getParametroDef() { return parametroDef; }
    public EventoCondicaoFonte getFonte() { return fonte; }
    public RegraParametro getRegraParametro() { return regraParametro; }
    public AvaliacaoResultado getResultadoEsperado() { return resultadoEsperado; }
    public RegraOperador getOperador() { return operador; }
    public BigDecimal getValorNumeric1() { return valorNumeric1; }
    public BigDecimal getValorNumeric2() { return valorNumeric2; }
    public Boolean getValorBoolean() { return valorBoolean; }
    public String getValorText() { return valorText; }
    public EventoAgregacao getAgregacao() { return agregacao; }
    public boolean isObrigatoria() { return obrigatoria; }
    public Integer getOrdem() { return ordem; }
    public boolean isAtivo() { return ativo; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEventoDefinicao(EventoDefinicao eventoDefinicao) { this.eventoDefinicao = eventoDefinicao; }
    public void setParametroDef(ParametroDef parametroDef) { this.parametroDef = parametroDef; }
    public void setFonte(EventoCondicaoFonte fonte) { this.fonte = fonte == null ? EventoCondicaoFonte.PARAMETRO_VALOR : fonte; }
    public void setRegraParametro(RegraParametro regraParametro) { this.regraParametro = regraParametro; }
    public void setResultadoEsperado(AvaliacaoResultado resultadoEsperado) { this.resultadoEsperado = resultadoEsperado; }
    public void setOperador(RegraOperador operador) { this.operador = operador; }
    public void setValorNumeric1(BigDecimal valorNumeric1) { this.valorNumeric1 = valorNumeric1; }
    public void setValorNumeric2(BigDecimal valorNumeric2) { this.valorNumeric2 = valorNumeric2; }
    public void setValorBoolean(Boolean valorBoolean) { this.valorBoolean = valorBoolean; }
    public void setValorText(String valorText) { this.valorText = valorText; }
    public void setAgregacao(EventoAgregacao agregacao) { this.agregacao = agregacao; }
    public void setObrigatoria(boolean obrigatoria) { this.obrigatoria = obrigatoria; }
    public void setOrdem(Integer ordem) { this.ordem = ordem; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
