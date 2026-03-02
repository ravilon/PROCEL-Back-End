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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String nome;

    protected Unidade() {}

    public Unidade(String nome) {
        this.nome = nome;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }

    public void setNome(String nome) { this.nome = nome; }
}