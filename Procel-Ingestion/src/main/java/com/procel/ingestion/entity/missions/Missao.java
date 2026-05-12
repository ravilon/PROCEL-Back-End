package com.procel.ingestion.entity.missions;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "missao",
        indexes = {
                @Index(name = "ix_missao_ativo", columnList = "ativo"),
                @Index(name = "ix_missao_created_at", columnList = "created_at")
        }
)
public class Missao {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "descricao", length = 1000)
    private String descricao;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo = "Individual";

    @Column(name = "value", nullable = false)
    private int value = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Missao() {}

    public Missao(String titulo, String descricao, String tipo, Integer value, boolean ativo) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.tipo = normalizeTipo(tipo);
        this.value = normalizeValue(value);
        this.ativo = ativo;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public String getTipo() { return tipo; }
    public int getValue() { return value; }
    public boolean isAtivo() { return ativo; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitulo(String titulo) { this.titulo = titulo; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setTipo(String tipo) { this.tipo = normalizeTipo(tipo); }
    public void setValue(Integer value) { this.value = normalizeValue(value); }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    private static String normalizeTipo(String tipo) {
        return tipo == null || tipo.isBlank() ? "Individual" : tipo.trim();
    }

    private static int normalizeValue(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
