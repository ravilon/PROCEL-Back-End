package com.procel.ingestion.entity.missions;

import com.procel.ingestion.entity.people.Pessoa;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "atividade",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_atividade_pessoa_modelo", columnNames = {"pessoa_id", "missao_id"})
        },
        indexes = {
                @Index(name = "ix_atividade_pessoa_status", columnList = "pessoa_id,status"),
                @Index(name = "ix_atividade_missao", columnList = "missao_id"),
                @Index(name = "ix_atividade_assigned_at", columnList = "assigned_at")
        }
)
public class Atividade {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false, foreignKey = @ForeignKey(name = "fk_atividade_pessoa"))
    private Pessoa pessoa;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "missao_id", nullable = false, foreignKey = @ForeignKey(name = "fk_atividade_missao"))
    private Missao missao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AtividadeStatus status = AtividadeStatus.PENDENTE;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Atividade() {}

    public Atividade(Pessoa pessoa, Missao missao, AtividadeStatus status) {
        this.pessoa = pessoa;
        this.missao = missao;
        this.status = status == null ? AtividadeStatus.PENDENTE : status;
        this.assignedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Pessoa getPessoa() { return pessoa; }
    public Missao getMissao() { return missao; }
    public AtividadeStatus getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setStatus(AtividadeStatus status) { this.status = status; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
