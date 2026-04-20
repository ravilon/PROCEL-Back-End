package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "regra_parametro",
        indexes = {
                @Index(name = "idx_regra_parametro_grupo_param", columnList = "grupo_regra_id,parametro_def_id"),
                @Index(name = "idx_regra_parametro_ativo", columnList = "ativo")
        }
)
public class RegraParametro {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_regra_id", nullable = false)
    private GrupoRegra grupoRegra;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parametro_def_id", nullable = false)
    private ParametroDef parametroDef;

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "operador", nullable = false, length = 30)
    private RegraOperador operador;

    @Column(name = "valor_numeric_1", precision = 18, scale = 6)
    private BigDecimal valorNumeric1;

    @Column(name = "valor_numeric_2", precision = 18, scale = 6)
    private BigDecimal valorNumeric2;

    @Column(name = "valor_text", length = 1000)
    private String valorText;

    @Column(name = "valor_boolean")
    private Boolean valorBoolean;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 30)
    private AvaliacaoResultado resultado;

    @Column(name = "severidade", nullable = false)
    private int severidade = 0;

    @Column(name = "prioridade", nullable = false)
    private int prioridade = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public RegraParametro() {}

    public UUID getId() { return id; }
    public GrupoRegra getGrupoRegra() { return grupoRegra; }
    public ParametroDef getParametroDef() { return parametroDef; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public RegraOperador getOperador() { return operador; }
    public BigDecimal getValorNumeric1() { return valorNumeric1; }
    public BigDecimal getValorNumeric2() { return valorNumeric2; }
    public String getValorText() { return valorText; }
    public Boolean getValorBoolean() { return valorBoolean; }
    public AvaliacaoResultado getResultado() { return resultado; }
    public int getSeveridade() { return severidade; }
    public int getPrioridade() { return prioridade; }
    public boolean isAtivo() { return ativo; }
    public Instant getCreatedAt() { return createdAt; }

    public void setGrupoRegra(GrupoRegra grupoRegra) { this.grupoRegra = grupoRegra; }
    public void setParametroDef(ParametroDef parametroDef) { this.parametroDef = parametroDef; }
    public void setNome(String nome) { this.nome = nome; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setOperador(RegraOperador operador) { this.operador = operador; }
    public void setValorNumeric1(BigDecimal valorNumeric1) { this.valorNumeric1 = valorNumeric1; }
    public void setValorNumeric2(BigDecimal valorNumeric2) { this.valorNumeric2 = valorNumeric2; }
    public void setValorText(String valorText) { this.valorText = valorText; }
    public void setValorBoolean(Boolean valorBoolean) { this.valorBoolean = valorBoolean; }
    public void setResultado(AvaliacaoResultado resultado) { this.resultado = resultado; }
    public void setSeveridade(int severidade) { this.severidade = severidade; }
    public void setPrioridade(int prioridade) { this.prioridade = prioridade; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
