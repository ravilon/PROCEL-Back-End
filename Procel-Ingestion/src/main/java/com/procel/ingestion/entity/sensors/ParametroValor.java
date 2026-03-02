package com.procel.ingestion.entity.sensors;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "parametro_valor",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "ux_valor_medicao_param",
            columnNames = {"medicao_id", "parametro_def_id"}
        )
    },
    indexes = {
        @Index(name = "idx_valor_medicao_id", columnList = "medicao_id"),
        @Index(name = "idx_valor_param_def_id", columnList = "parametro_def_id")
    }
)
public class ParametroValor {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "medicao_id", nullable = false)
    private Medicao medicao;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parametro_def_id", nullable = false)
    private ParametroDef parametroDef;

    @Column(name = "numeric_value", precision = 18, scale = 6)
    private BigDecimal numericValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "text_value", length = 1000)
    private String textValue;

    public ParametroValor() {
    }

    // 👇 ESTE CONSTRUTOR É O QUE ESTAVA FALTANDO
    public ParametroValor(Medicao medicao, ParametroDef parametroDef) {
        this.medicao = medicao;
        this.parametroDef = parametroDef;
    }

    public UUID getId() { return id; }

    public Medicao getMedicao() { return medicao; }
    public void setMedicao(Medicao medicao) { this.medicao = medicao; }

    public ParametroDef getParametroDef() { return parametroDef; }
    public void setParametroDef(ParametroDef parametroDef) { this.parametroDef = parametroDef; }

    public BigDecimal getNumericValue() { return numericValue; }
    public void setNumericValue(BigDecimal numericValue) { this.numericValue = numericValue; }

    public Boolean getBooleanValue() { return booleanValue; }
    public void setBooleanValue(Boolean booleanValue) { this.booleanValue = booleanValue; }

    public String getTextValue() { return textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }
}