package com.procel.ingestion.entity.people;

import com.procel.ingestion.entity.rooms.Disciplina;
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
        name = "aluno_disciplina",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_aluno_disciplina",
                        columnNames = {"pessoa_id", "disciplina_id", "turma", "periodo_letivo"}
                )
        },
        indexes = {
                @Index(
                        name = "ix_aluno_disciplina_pessoa_periodo",
                        columnList = "pessoa_id,periodo_letivo"
                ),
                @Index(
                        name = "ix_aluno_disciplina_disciplina",
                        columnList = "disciplina_id"
                )
        }
)
public class AlunoDisciplina {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "pessoa_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_aluno_disciplina_pessoa")
    )
    private Pessoa pessoa;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "disciplina_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_aluno_disciplina_disciplina")
    )
    private Disciplina disciplina;

    @Column(name = "turma", nullable = false, length = 80)
    private String turma;

    @Column(name = "periodo_letivo", nullable = false, length = 20)
    private String periodoLetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlunoDisciplinaStatus status = AlunoDisciplinaStatus.ATIVA;

    @Column(name = "vinculado_em", nullable = false)
    private Instant vinculadoEm = Instant.now();

    protected AlunoDisciplina() {}

    public AlunoDisciplina(
            Pessoa pessoa,
            Disciplina disciplina,
            String turma,
            String periodoLetivo,
            AlunoDisciplinaStatus status
    ) {
        this.pessoa = pessoa;
        this.disciplina = disciplina;
        this.turma = turma;
        this.periodoLetivo = periodoLetivo;
        this.status = status == null ? AlunoDisciplinaStatus.ATIVA : status;
        this.vinculadoEm = Instant.now();
    }

    public UUID getId() { return id; }
    public Pessoa getPessoa() { return pessoa; }
    public Disciplina getDisciplina() { return disciplina; }
    public String getTurma() { return turma; }
    public String getPeriodoLetivo() { return periodoLetivo; }
    public AlunoDisciplinaStatus getStatus() { return status; }
    public Instant getVinculadoEm() { return vinculadoEm; }

    public void setStatus(AlunoDisciplinaStatus status) { this.status = status; }
}

