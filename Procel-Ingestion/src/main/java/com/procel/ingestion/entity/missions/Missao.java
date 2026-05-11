package com.procel.ingestion.entity.missions;

import com.procel.ingestion.entity.people.Pessoa;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "missao",
        indexes = {
                @Index(name = "ix_missao_pessoa_status", columnList = "pessoa_id,status"),
                @Index(name = "ix_missao_created_at", columnList = "created_at")
        }
)
public class Missao {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false, foreignKey = @ForeignKey(name = "fk_missao_pessoa"))
    private Pessoa pessoa;

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "descricao", length = 1000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MissaoStatus status = MissaoStatus.PENDENTE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Missao() {}

    public Missao(Pessoa pessoa, String titulo, String descricao) {
        this.pessoa = pessoa;
        this.titulo = titulo;
        this.descricao = descricao;
        this.createdAt = Instant.now();
        this.status = MissaoStatus.PENDENTE;
    }

    public UUID getId() { return id; }
    public Pessoa getPessoa() { return pessoa; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public MissaoStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setPessoa(Pessoa pessoa) { this.pessoa = pessoa; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setStatus(MissaoStatus status) { this.status = status; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
