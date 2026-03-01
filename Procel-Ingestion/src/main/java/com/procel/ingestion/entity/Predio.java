package com.procel.ingestion.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "predio",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_predio_nome_campus", columnNames = {"campus_id", "nome"})
    },
    indexes = {
        @Index(name = "idx_predio_campus_id", columnList = "campus_id")
    }
)
public class Predio {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id", nullable = false)
    private Campus campus;

    @Column(name = "nome", length = 255, nullable = false)
    private String nome;

    protected Predio() {}

    public Predio(Campus campus, String nome) {
        this.campus = campus;
        this.nome = nome;
    }

    public UUID getId() { return id; }
    public Campus getCampus() { return campus; }
    public String getNome() { return nome; }

    public void setCampus(Campus campus) { this.campus = campus; }
    public void setNome(String nome) { this.nome = nome; }
}