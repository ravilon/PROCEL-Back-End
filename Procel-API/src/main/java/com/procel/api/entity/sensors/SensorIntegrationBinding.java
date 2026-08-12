package com.procel.api.entity.sensors;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensor_integration_binding")
public class SensorIntegrationBinding {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_external_id", nullable = false, referencedColumnName = "external_id")
    private Sensor sensor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private SensorIntegrationProfile profile;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    protected SensorIntegrationBinding() {}

    public SensorIntegrationBinding(Sensor sensor, SensorIntegrationProfile profile) {
        this.sensor = sensor;
        this.profile = profile;
    }

    public UUID getId() { return id; }
    public Sensor getSensor() { return sensor; }
    public SensorIntegrationProfile getProfile() { return profile; }
    public boolean isAtivo() { return ativo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeactivatedAt() { return deactivatedAt; }

    public void activate() {
        this.ativo = true;
        this.deactivatedAt = null;
    }

    public void deactivate(Instant now) {
        this.ativo = false;
        this.deactivatedAt = now;
    }
}
