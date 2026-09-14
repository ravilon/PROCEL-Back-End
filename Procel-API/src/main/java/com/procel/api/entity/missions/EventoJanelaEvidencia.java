package com.procel.api.entity.missions;

import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.ParametroValor;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "evento_janela_evidencia",
        indexes = {
                @Index(name = "ix_evento_janela_evidencia_janela", columnList = "evento_janela_avaliacao_id"),
                @Index(name = "ix_evento_janela_evidencia_medicao", columnList = "medicao_id")
        }
)
public class EventoJanelaEvidencia {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "evento_janela_avaliacao_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_janela_evidencia_janela")
    )
    private EventoJanelaAvaliacao janelaAvaliacao;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "medicao_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_janela_evidencia_medicao")
    )
    private Medicao medicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parametro_valor_id",
            foreignKey = @ForeignKey(name = "fk_evento_janela_evidencia_parametro_valor")
    )
    private ParametroValor parametroValor;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 30)
    private EventoJanelaEvidenciaPapel papel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected EventoJanelaEvidencia() {}

    public EventoJanelaEvidencia(
            EventoJanelaAvaliacao janelaAvaliacao,
            Medicao medicao,
            ParametroValor parametroValor,
            EventoJanelaEvidenciaPapel papel
    ) {
        this.janelaAvaliacao = janelaAvaliacao;
        this.medicao = medicao;
        this.parametroValor = parametroValor;
        this.papel = papel;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public EventoJanelaAvaliacao getJanelaAvaliacao() { return janelaAvaliacao; }
    public Medicao getMedicao() { return medicao; }
    public ParametroValor getParametroValor() { return parametroValor; }
    public EventoJanelaEvidenciaPapel getPapel() { return papel; }
    public Instant getCreatedAt() { return createdAt; }
}
