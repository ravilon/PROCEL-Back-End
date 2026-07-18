package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "avaliacao_parametro_valor",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_avaliacao_valor_regra",
                        columnNames = {"parametro_valor_id", "regra_parametro_id"}
                )
        },
        indexes = {
                @Index(name = "idx_avaliacao_parametro_valor", columnList = "parametro_valor_id"),
                @Index(name = "idx_avaliacao_regra_parametro", columnList = "regra_parametro_id"),
                @Index(name = "idx_avaliacao_resultado", columnList = "resultado")
        }
)
public class AvaliacaoParametroValor {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parametro_valor_id", nullable = false)
    private ParametroValor parametroValor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regra_parametro_id")
    private RegraParametro regraParametro;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 30)
    private AvaliacaoResultado resultado;

    @Column(name = "severidade", nullable = false)
    private int severidade = 0;

    @Column(name = "mensagem", length = 500)
    private String mensagem;

    @Column(name = "avaliado_em", nullable = false)
    private Instant avaliadoEm = Instant.now();

    public AvaliacaoParametroValor() {}

    public AvaliacaoParametroValor(ParametroValor parametroValor, RegraParametro regraParametro, AvaliacaoResultado resultado, int severidade, String mensagem) {
        this.parametroValor = parametroValor;
        this.regraParametro = regraParametro;
        this.resultado = resultado;
        this.severidade = severidade;
        this.mensagem = mensagem;
        this.avaliadoEm = Instant.now();
    }

    public UUID getId() { return id; }
    public ParametroValor getParametroValor() { return parametroValor; }
    public RegraParametro getRegraParametro() { return regraParametro; }
    public AvaliacaoResultado getResultado() { return resultado; }
    public int getSeveridade() { return severidade; }
    public String getMensagem() { return mensagem; }
    public Instant getAvaliadoEm() { return avaliadoEm; }

    public void setParametroValor(ParametroValor parametroValor) { this.parametroValor = parametroValor; }
    public void setRegraParametro(RegraParametro regraParametro) { this.regraParametro = regraParametro; }
    public void setResultado(AvaliacaoResultado resultado) { this.resultado = resultado; }
    public void setSeveridade(int severidade) { this.severidade = severidade; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
}
