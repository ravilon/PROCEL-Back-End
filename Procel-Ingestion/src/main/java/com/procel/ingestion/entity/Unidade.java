package com.procel.ingestion.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "unidade",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_unidade_nome", columnNames = {"nome"})
    }
)
public class Unidade {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "nome", length = 150, nullable = false)
    private String nome;

    protected Unidade() {}

    public Unidade(String nome) {
        this.nome = nome;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }

    public void setNome(String nome) { this.nome = nome; }
}