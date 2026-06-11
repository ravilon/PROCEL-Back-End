package com.procel.ingestion.entity.rooms;

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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "ocorrencia_aula",
        indexes = {
                @Index(
                        name = "ix_ocorrencia_aula_compartimento_data",
                        columnList = "compartimento_id,data"
                ),
                @Index(
                        name = "ix_ocorrencia_aula_disciplina_data",
                        columnList = "disciplina_id,data"
                ),
                @Index(
                        name = "ix_ocorrencia_aula_data_turno_periodo",
                        columnList = "data,turno,periodo"
                )
        }
)
public class OcorrenciaAula {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "compartimento_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ocorrencia_aula_compartimento")
    )
    private Compartimento compartimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "disciplina_id",
            foreignKey = @ForeignKey(name = "fk_ocorrencia_aula_disciplina")
    )
    private Disciplina disciplina;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "turno", nullable = false)
    private Integer turno;

    @Column(name = "periodo", nullable = false)
    private Integer periodo;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(name = "turma", length = 80)
    private String turma;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private OcorrenciaAulaTipo tipo;

    @Column(name = "descricao", nullable = false, length = 1000)
    private String descricao;

    @Column(name = "source", length = 60)
    private String source;

    @Column(name = "sincronizado_em", nullable = false)
    private Instant sincronizadoEm = Instant.now();

    protected OcorrenciaAula() {}

    public OcorrenciaAula(
            Compartimento compartimento,
            Disciplina disciplina,
            LocalDate data,
            Integer turno,
            Integer periodo,
            LocalTime horaInicio,
            LocalTime horaFim,
            String turma,
            OcorrenciaAulaTipo tipo,
            String descricao,
            String source
    ) {
        this.compartimento = compartimento;
        this.disciplina = disciplina;
        this.data = data;
        this.turno = turno;
        this.periodo = periodo;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.turma = turma;
        this.tipo = tipo;
        this.descricao = descricao;
        this.source = source;
        this.sincronizadoEm = Instant.now();
    }

    public UUID getId() { return id; }
    public Compartimento getCompartimento() { return compartimento; }
    public Disciplina getDisciplina() { return disciplina; }
    public LocalDate getData() { return data; }
    public Integer getTurno() { return turno; }
    public Integer getPeriodo() { return periodo; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public String getTurma() { return turma; }
    public OcorrenciaAulaTipo getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public String getSource() { return source; }
    public Instant getSincronizadoEm() { return sincronizadoEm; }

    public void setDisciplina(Disciplina disciplina) { this.disciplina = disciplina; }
    public void setTurma(String turma) { this.turma = turma; }
    public void setTipo(OcorrenciaAulaTipo tipo) { this.tipo = tipo; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setSource(String source) { this.source = source; }
    public void setSincronizadoEm(Instant sincronizadoEm) { this.sincronizadoEm = sincronizadoEm; }
}
