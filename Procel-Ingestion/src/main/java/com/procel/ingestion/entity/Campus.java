package com.procel.ingestion.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "campus",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_campus_nome", columnNames = {"nome"})
    }
)
public class Campus {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "nome", length = 255, nullable = false)
    private String nome;

    protected Campus() {}

    public Campus(String nome) {
        this.nome = nome;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
}