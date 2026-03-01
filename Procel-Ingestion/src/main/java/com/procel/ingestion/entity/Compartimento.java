package com.procel.ingestion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "compartimento",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_compartimento_external_id", columnNames = {"external_id"}),
        @UniqueConstraint(name = "ux_compartimento_predio_nome", columnNames = {"predio_id", "nome"})
    },
    indexes = {
        @Index(name = "idx_compartimento_predio_id", columnList = "predio_id"),
        @Index(name = "idx_compartimento_unidade_id", columnList = "unidade_id")
    }
)
public class Compartimento {

    @Id
    @GeneratedValue
    private UUID id;

    // Cobalto: compartimento_id
    @Column(name = "external_id", nullable = false)
    private Long externalId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "predio_id", nullable = false)
    private Predio predio;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_id", nullable = false)
    private Unidade unidade;

    @Column(name = "nome", length = 150, nullable = false)
    private String nome;

    // "Circulação", "Sala", etc. (utilizacao_compartimento_descricao)
    @Column(name = "tipo", length = 80, nullable = false)
    private String tipo;

    @Column(name = "pavimento")
    private Integer pavimento;

    @Column(name = "capacidade")
    private Integer capacidade;

    @Column(name = "area", precision = 10, scale = 2)
    private BigDecimal area;

    // Se quiser guardar lotacao do payload como texto
    @Column(name = "lotacao_raw", length = 40)
    private String lotacaoRaw;

    public Compartimento() {}

    public Compartimento(Long externalId, Predio predio, Unidade unidade, String nome, String tipo) {
        this.externalId = externalId;
        this.predio = predio;
        this.unidade = unidade;
        this.nome = nome;
        this.tipo = tipo;
    }

    public UUID getId() { return id; }
    public Long getExternalId() { return externalId; }
    public Predio getPredio() { return predio; }
    public Unidade getUnidade() { return unidade; }
    public String getNome() { return nome; }
    public String getTipo() { return tipo; }
    public Integer getPavimento() { return pavimento; }
    public Integer getCapacidade() { return capacidade; }
    public BigDecimal getArea() { return area; }
    public String getLotacaoRaw() { return lotacaoRaw; }

    public void setExternalId(Long externalId) { this.externalId = externalId; }
    public void setPredio(Predio predio) { this.predio = predio; }
    public void setUnidade(Unidade unidade) { this.unidade = unidade; }
    public void setNome(String nome) { this.nome = nome; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public void setPavimento(Integer pavimento) { this.pavimento = pavimento; }
    public void setCapacidade(Integer capacidade) { this.capacidade = capacidade; }
    public void setArea(BigDecimal area) { this.area = area; }
    public void setLotacaoRaw(String lotacaoRaw) { this.lotacaoRaw = lotacaoRaw; }
}