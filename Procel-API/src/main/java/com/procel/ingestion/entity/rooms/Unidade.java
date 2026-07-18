package com.procel.ingestion.entity.rooms;

import jakarta.persistence.*;

@Entity
@Table(
    name = "unidade",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_unidade_nome", columnNames = {"nome"})
    }
)
public class Unidade {

    @Id
    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    protected Unidade() {}

    public Unidade(String nome) {
        this.nome = nome;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
}