package com.procel.ingestion.entity.rooms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "disciplina")
public class Disciplina {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "unidade_sigla", length = 80)
    private String unidadeSigla;

    protected Disciplina() {}

    public Disciplina(Long id, String nome, String unidadeSigla) {
        this.id = id;
        this.nome = nome;
        this.unidadeSigla = unidadeSigla;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getUnidadeSigla() { return unidadeSigla; }

    public void setNome(String nome) { this.nome = nome; }
    public void setUnidadeSigla(String unidadeSigla) { this.unidadeSigla = unidadeSigla; }
}
