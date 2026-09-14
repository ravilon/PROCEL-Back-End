package com.procel.api.entity.missions;

import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.rooms.PeriodoAula;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "evento_janela_avaliacao",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_evento_janela_chave_idempotencia",
                        columnNames = "chave_idempotencia"
                )
        },
        indexes = {
                @Index(name = "ix_evento_janela_status_proxima", columnList = "status,proxima_avaliacao_em"),
                @Index(name = "ix_evento_janela_lease", columnList = "status,lease_until"),
                @Index(name = "ix_evento_janela_evento_compartimento", columnList = "evento_definicao_id,compartimento_id,inicio_em")
        }
)
public class EventoJanelaAvaliacao {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "evento_definicao_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_janela_evento")
    )
    private EventoDefinicao eventoDefinicao;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "compartimento_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_janela_compartimento")
    )
    private Compartimento compartimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "periodo_aula_id",
            foreignKey = @ForeignKey(name = "fk_evento_janela_periodo_aula")
    )
    private PeriodoAula periodoAula;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventoJanelaAvaliacaoStatus status = EventoJanelaAvaliacaoStatus.ABERTA;

    @Column(name = "inicio_em", nullable = false)
    private Instant inicioEm;

    @Column(name = "fim_previsto_em", nullable = false)
    private Instant fimPrevistoEm;

    @Column(name = "ultima_medicao_em")
    private Instant ultimaMedicaoEm;

    @Column(name = "proxima_avaliacao_em", nullable = false)
    private Instant proximaAvaliacaoEm;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "chave_idempotencia", nullable = false, length = 200)
    private String chaveIdempotencia;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto_snapshot", nullable = false, columnDefinition = "jsonb")
    private String contextoSnapshot;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected EventoJanelaAvaliacao() {}

    public EventoJanelaAvaliacao(
            EventoDefinicao eventoDefinicao,
            Compartimento compartimento,
            PeriodoAula periodoAula,
            Instant inicioEm,
            Instant fimPrevistoEm,
            Instant ultimaMedicaoEm,
            Instant proximaAvaliacaoEm,
            String chaveIdempotencia,
            String contextoSnapshot
    ) {
        this.eventoDefinicao = eventoDefinicao;
        this.compartimento = compartimento;
        this.periodoAula = periodoAula;
        this.status = EventoJanelaAvaliacaoStatus.ABERTA;
        this.inicioEm = inicioEm;
        this.fimPrevistoEm = fimPrevistoEm;
        this.ultimaMedicaoEm = ultimaMedicaoEm;
        this.proximaAvaliacaoEm = proximaAvaliacaoEm;
        this.chaveIdempotencia = chaveIdempotencia;
        this.contextoSnapshot = contextoSnapshot;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public EventoDefinicao getEventoDefinicao() { return eventoDefinicao; }
    public Compartimento getCompartimento() { return compartimento; }
    public PeriodoAula getPeriodoAula() { return periodoAula; }
    public EventoJanelaAvaliacaoStatus getStatus() { return status; }
    public Instant getInicioEm() { return inicioEm; }
    public Instant getFimPrevistoEm() { return fimPrevistoEm; }
    public Instant getUltimaMedicaoEm() { return ultimaMedicaoEm; }
    public Instant getProximaAvaliacaoEm() { return proximaAvaliacaoEm; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public int getAttempts() { return attempts; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public String getContextoSnapshot() { return contextoSnapshot; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
