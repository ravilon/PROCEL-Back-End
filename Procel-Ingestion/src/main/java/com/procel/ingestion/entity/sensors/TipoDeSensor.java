package com.procel.ingestion.entity.sensors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tipo_de_sensor")
public class TipoDeSensor {

    @Id
    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    public TipoDeSensor() {}

    public TipoDeSensor(String nome) {
        this.nome = nome;
    }

    public String getNome() { return nome; }

    public void setNome(String nome) { this.nome = nome; }
}