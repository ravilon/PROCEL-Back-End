package com.procel.api.entity.missions;

import com.procel.api.entity.people.Pessoa;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "atividade",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_atividade_pessoa_missao_ciclo", columnNames = {"pessoa_id", "missao_id", "chave_ciclo"})
        },
        indexes = {
                @Index(name = "ix_atividade_pessoa_status", columnList = "pessoa_id,status"),
                @Index(name = "ix_atividade_missao", columnList = "missao_id"),
                @Index(name = "ix_atividade_pessoa_missao_ciclo", columnList = "pessoa_id,missao_id,chave_ciclo"),
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

    @Column(name = "chave_ciclo", nullable = false, length = 160)
    private String chaveCiclo = "UNICA";

    @Enumerated(EnumType.STRING)
    @Column(name = "ciclo_tipo", nullable = false, length = 40)
    private MissaoCicloTipo cicloTipo = MissaoCicloTipo.UNICA;

    @Column(name = "ciclo_inicio")
    private Instant cicloInicio;

    @Column(name = "ciclo_fim")
    private Instant cicloFim;

    @Column(name = "progresso_atual", nullable = false)
    private int progressoAtual = 0;

    @Column(name = "progresso_necessario", nullable = false)
    private int progressoNecessario = 1;

    @Column(name = "ultimo_evento_em")
    private Instant ultimoEventoEm;

    @Column(name = "conclusao_automatica", nullable = false)
    private boolean conclusaoAutomatica = true;

    protected Atividade() {}

    public Atividade(Pessoa pessoa, Missao missao, AtividadeStatus status) {
        this(pessoa, missao, status, "UNICA", MissaoCicloTipo.UNICA, null, null,
                0, missao == null ? 1 : missao.getProgressoNecessario(),
                missao == null || missao.isConclusaoAutomatica());
    }

    public Atividade(
            Pessoa pessoa,
            Missao missao,
            AtividadeStatus status,
            String chaveCiclo,
            MissaoCicloTipo cicloTipo,
            Instant cicloInicio,
            Instant cicloFim,
            int progressoAtual,
            int progressoNecessario,
            boolean conclusaoAutomatica
    ) {
        this.pessoa = pessoa;
        this.missao = missao;
        this.status = status == null ? AtividadeStatus.PENDENTE : status;
        this.chaveCiclo = chaveCiclo == null || chaveCiclo.isBlank() ? "UNICA" : chaveCiclo;
        this.cicloTipo = cicloTipo == null ? MissaoCicloTipo.UNICA : cicloTipo;
        this.cicloInicio = cicloInicio;
        this.cicloFim = cicloFim;
        this.progressoAtual = Math.max(0, progressoAtual);
        this.progressoNecessario = Math.max(1, progressoNecessario);
        this.conclusaoAutomatica = conclusaoAutomatica;
        this.assignedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Pessoa getPessoa() { return pessoa; }
    public Missao getMissao() { return missao; }
    public AtividadeStatus getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getChaveCiclo() { return chaveCiclo; }
    public MissaoCicloTipo getCicloTipo() { return cicloTipo; }
    public Instant getCicloInicio() { return cicloInicio; }
    public Instant getCicloFim() { return cicloFim; }
    public int getProgressoAtual() { return progressoAtual; }
    public int getProgressoNecessario() { return progressoNecessario; }
    public Instant getUltimoEventoEm() { return ultimoEventoEm; }
    public boolean isConclusaoAutomatica() { return conclusaoAutomatica; }

    public void setStatus(AtividadeStatus status) { this.status = status; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setUltimoEventoEm(Instant ultimoEventoEm) { this.ultimoEventoEm = ultimoEventoEm; }
}
