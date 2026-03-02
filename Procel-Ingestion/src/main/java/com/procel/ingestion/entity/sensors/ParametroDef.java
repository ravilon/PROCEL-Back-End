package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
    name = "parametro_def",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "ux_parametro_def_tipo_nome",
            columnNames = {"tipo_nome", "nome"}
        )
    },
    indexes = {
        @Index(name = "idx_parametro_def_tipo_nome", columnList = "tipo_nome")
    }
)
public class ParametroDef {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_nome", nullable = false)
    private TipoDeSensor tipo;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome; // chave no payload/raw

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private DataType dataType;

    @Column(name = "numeric_unit", length = 40)
    private String numericUnit;

    public ParametroDef() {}

    public ParametroDef(TipoDeSensor tipo, String nome, String descricao, DataType dataType, String numericUnit) {
        this.tipo = tipo;
        this.nome = nome;
        this.descricao = descricao;
        this.dataType = dataType;
        this.numericUnit = numericUnit;
    }

    public UUID getId() { return id; }
    public TipoDeSensor getTipo() { return tipo; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public DataType getDataType() { return dataType; }
    public String getNumericUnit() { return numericUnit; }

    public void setTipo(TipoDeSensor tipo) { this.tipo = tipo; }
    public void setNome(String nome) { this.nome = nome; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setDataType(DataType dataType) { this.dataType = dataType; }
    public void setNumericUnit(String numericUnit) { this.numericUnit = numericUnit; }
}