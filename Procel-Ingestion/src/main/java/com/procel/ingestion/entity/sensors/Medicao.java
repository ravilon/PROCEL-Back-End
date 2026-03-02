package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "medicao",
    indexes = {
        // FK agora referencia sensor.external_id (String)
        @Index(name = "idx_medicao_sensor_ts", columnList = "sensor_external_id,timestamp")
    }
)
public class Medicao {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
        name = "sensor_external_id",
        nullable = false,
        referencedColumnName = "external_id" // deixa explícito (PK do Sensor)
    )
    private Sensor sensor;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "recebido_em")
    private Instant recebidoEm;

    @Column(name = "source", length = 60)
    private String source;

    public Medicao() {}

    public Medicao(Sensor sensor, Instant timestamp, Instant recebidoEm, String source) {
        this.sensor = sensor;
        this.timestamp = timestamp;
        this.recebidoEm = recebidoEm;
        this.source = source;
    }

    public UUID getId() { return id; }
    public Sensor getSensor() { return sensor; }
    public Instant getTimestamp() { return timestamp; }
    public Instant getRecebidoEm() { return recebidoEm; }
    public String getSource() { return source; }

    public void setSensor(Sensor sensor) { this.sensor = sensor; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setRecebidoEm(Instant recebidoEm) { this.recebidoEm = recebidoEm; }
    public void setSource(String source) { this.source = source; }
}