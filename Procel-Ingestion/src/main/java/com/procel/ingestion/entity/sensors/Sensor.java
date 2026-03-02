package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;
import java.util.UUID;
import com.procel.ingestion.entity.rooms.Compartimento;

@Entity
@Table(
    name = "sensor",
    uniqueConstraints = {
        // identificador do mundo real (serial/mac/uid do gateway)
        @UniqueConstraint(name = "ux_sensor_external_id", columnNames = {"external_id"})
    },
    indexes = {
        @Index(name = "idx_sensor_compartimento_id", columnList = "compartimento_id"),
        @Index(name = "idx_sensor_tipo_id", columnList = "tipo_id")
    }
)
public class Sensor {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoDeSensor tipo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "compartimento_id", nullable = false)
    private Compartimento compartimento;

    public Sensor() {}

    public Sensor(String externalId, String nome, TipoDeSensor tipo, Compartimento compartimento) {
        this.externalId = externalId;
        this.nome = nome;
        this.tipo = tipo;
        this.compartimento = compartimento;
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getNome() { return nome; }
    public TipoDeSensor getTipo() { return tipo; }
    public Compartimento getCompartimento() { return compartimento; }

    public void setExternalId(String externalId) { this.externalId = externalId; }
    public void setNome(String nome) { this.nome = nome; }
    public void setTipo(TipoDeSensor tipo) { this.tipo = tipo; }
    public void setCompartimento(Compartimento compartimento) { this.compartimento = compartimento; }
}