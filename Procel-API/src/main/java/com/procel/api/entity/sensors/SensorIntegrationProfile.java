package com.procel.api.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensor_integration_profile")
public class SensorIntegrationProfile {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MedicaoIngestaoSource source;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SensorIntegrationProfile() {}

    public SensorIntegrationProfile(String nome, String descricao, MedicaoIngestaoSource source) {
        this.nome = nome;
        this.descricao = descricao;
        this.source = source;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public MedicaoIngestaoSource getSource() { return source; }
    public boolean isAtivo() { return ativo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String nome, String descricao, MedicaoIngestaoSource source) {
        this.nome = nome;
        this.descricao = descricao;
        this.source = source;
        this.updatedAt = Instant.now();
    }

    public void updatePublished(String nome, String descricao) {
        this.nome = nome;
        this.descricao = descricao;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.ativo = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.ativo = false;
        this.updatedAt = Instant.now();
    }
}
