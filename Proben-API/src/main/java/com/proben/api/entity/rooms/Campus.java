package com.proben.api.entity.rooms;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
    name = "campus",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_campus_nome", columnNames = {"nome"})
    }
)
/**
 * Componente de campus usado pelo modulo API.
 */
public class Campus {
    @Id
    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    protected Campus() {}

    public Campus(String nome) {
        this.nome = nome;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
}
