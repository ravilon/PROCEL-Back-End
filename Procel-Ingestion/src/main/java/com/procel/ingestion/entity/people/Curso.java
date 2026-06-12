package com.procel.ingestion.entity.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "curso",
        indexes = @Index(name = "ix_curso_nome", columnList = "nome")
)
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "unidade_sigla", length = 80)
    private String unidadeSigla;

    protected Curso() {}

    public Curso(String nome, String unidadeSigla) {
        this.nome = nome;
        this.unidadeSigla = unidadeSigla;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getUnidadeSigla() { return unidadeSigla; }
    public void setNome(String nome) { this.nome = nome; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }
}
