package com.procel.ingestion.entity.rooms;

import jakarta.persistence.*;

@Entity
@Table(
    name = "campus",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_campus_nome", columnNames = {"nome"})
    }
)
public class Campus {
    @Id
    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    protected Campus() {}

    public Campus(String nome) {
        this.nome = nome;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
}