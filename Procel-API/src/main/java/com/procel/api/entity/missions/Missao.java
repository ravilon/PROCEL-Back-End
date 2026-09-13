package com.procel.api.entity.missions;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "missao",
        indexes = {
                @Index(name = "ix_missao_ativo", columnList = "ativo"),
                @Index(name = "ix_missao_created_at", columnList = "created_at"),
                @Index(name = "ix_missao_parent_id", columnList = "parent_id")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "ciclo_tipo", nullable = false, length = 40)
    private MissaoCicloTipo cicloTipo = MissaoCicloTipo.UNICA;

    @Column(name = "progresso_necessario", nullable = false)
    private int progressoNecessario = 1;

    @Column(name = "conclusao_automatica", nullable = false)
    private boolean conclusaoAutomatica = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Missao parent;

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
    public MissaoCicloTipo getCicloTipo() { return cicloTipo; }
    public int getProgressoNecessario() { return progressoNecessario; }
    public boolean isConclusaoAutomatica() { return conclusaoAutomatica; }
    public Instant getCreatedAt() { return createdAt; }
    public Missao getParent() { return parent; }

    public void setTitulo(String titulo) { this.titulo = titulo; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setTipo(String tipo) { this.tipo = normalizeTipo(tipo); }
    public void setValue(Integer value) { this.value = normalizeValue(value); }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public void setCicloTipo(MissaoCicloTipo cicloTipo) {
        this.cicloTipo = cicloTipo == null ? MissaoCicloTipo.UNICA : cicloTipo;
    }
    public void setProgressoNecessario(Integer progressoNecessario) {
        if (progressoNecessario == null) {
            this.progressoNecessario = 1;
            return;
        }
        if (progressoNecessario < 1) {
            throw new IllegalArgumentException("progressoNecessario must be >= 1");
        }
        this.progressoNecessario = progressoNecessario;
    }
    public void setConclusaoAutomatica(Boolean conclusaoAutomatica) {
        this.conclusaoAutomatica = conclusaoAutomatica == null || conclusaoAutomatica;
    }
    public void setParent(Missao parent) { this.parent = parent; }

    private static String normalizeTipo(String tipo) {
        return tipo == null || tipo.isBlank() ? "Individual" : tipo.trim();
    }

    private static int normalizeValue(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
