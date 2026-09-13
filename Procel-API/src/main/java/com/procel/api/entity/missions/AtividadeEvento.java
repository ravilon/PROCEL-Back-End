package com.procel.api.entity.missions;

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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "atividade_evento",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_atividade_evento_ocorrencia_tipo",
                        columnNames = {"atividade_id", "evento_ocorrencia_id", "tipo"}
                )
        },
        indexes = {
                @Index(name = "ix_atividade_evento_atividade", columnList = "atividade_id"),
                @Index(name = "ix_atividade_evento_ocorrencia", columnList = "evento_ocorrencia_id")
        }
)
public class AtividadeEvento {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "atividade_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_atividade_evento_atividade")
    )
    private Atividade atividade;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "evento_ocorrencia_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_atividade_evento_ocorrencia")
    )
    private EventoOcorrencia eventoOcorrencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private AtividadeEventoTipo tipo;

    @Column(name = "progresso_adicionado", nullable = false)
    private int progressoAdicionado;

    @Column(name = "processado_em", nullable = false)
    private Instant processadoEm = Instant.now();

    protected AtividadeEvento() {}

    public AtividadeEvento(
            Atividade atividade,
            EventoOcorrencia eventoOcorrencia,
            AtividadeEventoTipo tipo,
            int progressoAdicionado,
            Instant processadoEm
    ) {
        if (progressoAdicionado < 0) {
            throw new IllegalArgumentException("progressoAdicionado must be >= 0");
        }
        this.atividade = atividade;
        this.eventoOcorrencia = eventoOcorrencia;
        this.tipo = tipo;
        this.progressoAdicionado = progressoAdicionado;
        this.processadoEm = processadoEm == null ? Instant.now() : processadoEm;
    }

    public UUID getId() { return id; }
    public Atividade getAtividade() { return atividade; }
    public EventoOcorrencia getEventoOcorrencia() { return eventoOcorrencia; }
    public AtividadeEventoTipo getTipo() { return tipo; }
    public int getProgressoAdicionado() { return progressoAdicionado; }
    public Instant getProcessadoEm() { return processadoEm; }
}
