package com.procel.ingestion.entity.rooms;

import jakarta.persistence.*;

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
    @Column(name = "id", nullable = false, length = 600)
    private String id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id", nullable = false)
    private Campus campus;

    @Column(name = "nome", length = 255, nullable = false)
    private String nome;

    protected Predio() {}

    public Predio(Campus campus, String nome) {
        this.campus = campus;
        this.nome = nome;
        this.id = campus.getNome().trim() + "|" + nome.trim();
    }

    public String getId() { return id; }
    public Campus getCampus() { return campus; }
    public String getNome() { return nome; }

    public void setCampus(Campus campus) { this.campus = campus; }
    public void setNome(String nome) { this.nome = nome; }
}