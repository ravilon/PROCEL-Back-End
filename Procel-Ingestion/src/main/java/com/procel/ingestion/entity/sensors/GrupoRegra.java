package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "grupo_regra",
        indexes = {
                @Index(name = "idx_grupo_regra_ativo", columnList = "ativo")
        }
)
public class GrupoRegra {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public GrupoRegra() {}

    public GrupoRegra(String nome, String descricao, boolean ativo) {
        this.nome = nome;
        this.descricao = descricao;
        this.ativo = ativo;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public boolean isAtivo() { return ativo; }
    public Instant getCreatedAt() { return createdAt; }

    public void setNome(String nome) { this.nome = nome; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
