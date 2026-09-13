package com.procel.api.entity.missions;

import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.rooms.PeriodoAula;
import com.procel.api.entity.sensors.Sensor;
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
        name = "evento_ocorrencia",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_evento_ocorrencia_chave_idempotencia",
                        columnNames = "chave_idempotencia"
                )
        },
        indexes = {
                @Index(name = "ix_evento_ocorrencia_status_detectado", columnList = "status,detectado_em"),
                @Index(name = "ix_evento_ocorrencia_evento", columnList = "evento_definicao_id"),
                @Index(name = "ix_evento_ocorrencia_compartimento_inicio", columnList = "compartimento_id,inicio_em")
        }
)
public class EventoOcorrencia {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "evento_definicao_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_ocorrencia_evento")
    )
    private EventoDefinicao eventoDefinicao;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "compartimento_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_ocorrencia_compartimento")
    )
    private Compartimento compartimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "periodo_aula_id",
            foreignKey = @ForeignKey(name = "fk_evento_ocorrencia_periodo_aula")
    )
    private PeriodoAula periodoAula;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "sensor_external_id",
            referencedColumnName = "external_id",
            foreignKey = @ForeignKey(name = "fk_evento_ocorrencia_sensor")
    )
    private Sensor sensor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventoOcorrenciaStatus status = EventoOcorrenciaStatus.DETECTADO;

    @Column(name = "inicio_em", nullable = false)
    private Instant inicioEm;

    @Column(name = "fim_em")
    private Instant fimEm;

    @Column(name = "detectado_em", nullable = false)
    private Instant detectadoEm;

    @Column(name = "chave_idempotencia", nullable = false, length = 160)
    private String chaveIdempotencia;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto_snapshot", nullable = false, columnDefinition = "jsonb")
    private String contextoSnapshot;

    @Column(name = "conteudo_fingerprint", nullable = false, length = 64)
    private String conteudoFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected EventoOcorrencia() {}

    public EventoOcorrencia(
            EventoDefinicao eventoDefinicao,
            Compartimento compartimento,
            PeriodoAula periodoAula,
            Sensor sensor,
            Instant inicioEm,
            Instant fimEm,
            Instant detectadoEm,
            String chaveIdempotencia,
            String contextoSnapshot,
            String conteudoFingerprint
    ) {
        this.eventoDefinicao = eventoDefinicao;
        this.compartimento = compartimento;
        this.periodoAula = periodoAula;
        this.sensor = sensor;
        this.inicioEm = inicioEm;
        this.fimEm = fimEm;
        this.detectadoEm = detectadoEm;
        this.chaveIdempotencia = chaveIdempotencia;
        this.contextoSnapshot = contextoSnapshot;
        this.conteudoFingerprint = conteudoFingerprint;
        this.status = EventoOcorrenciaStatus.DETECTADO;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public EventoDefinicao getEventoDefinicao() { return eventoDefinicao; }
    public Compartimento getCompartimento() { return compartimento; }
    public PeriodoAula getPeriodoAula() { return periodoAula; }
    public Sensor getSensor() { return sensor; }
    public EventoOcorrenciaStatus getStatus() { return status; }
    public Instant getInicioEm() { return inicioEm; }
    public Instant getFimEm() { return fimEm; }
    public Instant getDetectadoEm() { return detectadoEm; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public String getContextoSnapshot() { return contextoSnapshot; }
    public String getConteudoFingerprint() { return conteudoFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateStatus(EventoOcorrenciaStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
