package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "tipo_de_sensor",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_tipo_de_sensor_nome", columnNames = {"nome"})
    }
)
public class TipoDeSensor {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    public TipoDeSensor() {}

    public TipoDeSensor(String nome) {
        this.nome = nome;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }

    public void setNome(String nome) { this.nome = nome; }
}