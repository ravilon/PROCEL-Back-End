package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "sensor_grupo_regra",
        indexes = {
                @Index(name = "idx_sensor_grupo_regra_lookup", columnList = "sensor_external_id,status,valido_de,valido_ate"),
                @Index(name = "idx_sensor_grupo_regra_grupo", columnList = "grupo_regra_id")
        }
)
public class SensorGrupoRegra {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_external_id", nullable = false, referencedColumnName = "external_id")
    private Sensor sensor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_regra_id", nullable = false)
    private GrupoRegra grupoRegra;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SensorGrupoRegraStatus status = SensorGrupoRegraStatus.RASCUNHO;

    @Column(name = "valido_de")
    private Instant validoDe;

    @Column(name = "valido_ate")
    private Instant validoAte;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SensorGrupoRegra() {}

    public SensorGrupoRegra(Sensor sensor, GrupoRegra grupoRegra, SensorGrupoRegraStatus status, Instant validoDe, Instant validoAte) {
        this.sensor = sensor;
        this.grupoRegra = grupoRegra;
        this.status = status;
        this.validoDe = validoDe;
        this.validoAte = validoAte;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Sensor getSensor() { return sensor; }
    public GrupoRegra getGrupoRegra() { return grupoRegra; }
    public SensorGrupoRegraStatus getStatus() { return status; }
    public Instant getValidoDe() { return validoDe; }
    public Instant getValidoAte() { return validoAte; }
    public Instant getCreatedAt() { return createdAt; }

    public void setSensor(Sensor sensor) { this.sensor = sensor; }
    public void setGrupoRegra(GrupoRegra grupoRegra) { this.grupoRegra = grupoRegra; }
    public void setStatus(SensorGrupoRegraStatus status) { this.status = status; }
    public void setValidoDe(Instant validoDe) { this.validoDe = validoDe; }
    public void setValidoAte(Instant validoAte) { this.validoAte = validoAte; }
}
