package com.procel.api.entity.shared;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "contexto_compartilhado")
public class ContextoCompartilhado {
    @Id private UUID id;
    @Column(nullable = false, length = 160) private String nome;
    @Column(length = 1000) private String descricao;
    @Column(nullable = false) private boolean ativo = true;
    @Column(precision = 9, scale = 6) private BigDecimal latitude;
    @Column(precision = 9, scale = 6) private BigDecimal longitude;
    @Column(nullable = false, length = 80) private String timezone = "America/Sao_Paulo";

    public ContextoCompartilhado() {}
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public boolean isAtivo() { return ativo; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getTimezone() { return timezone; }
}
