package com.procel.api.entity.missions;

import com.procel.api.entity.sensors.Medicao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "evento_avaliacao_request",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_evento_avaliacao_request_medicao",
                        columnNames = "medicao_id"
                )
        },
        indexes = {
                @Index(name = "ix_evento_avaliacao_request_status_available", columnList = "status,available_at"),
                @Index(name = "ix_evento_avaliacao_request_lease", columnList = "status,lease_until")
        }
)
public class EventoAvaliacaoRequest {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "medicao_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_avaliacao_request_medicao")
    )
    private Medicao medicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventoAvaliacaoRequestStatus status = EventoAvaliacaoRequestStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected EventoAvaliacaoRequest() {}

    public EventoAvaliacaoRequest(Medicao medicao, Instant availableAt) {
        this.medicao = medicao;
        this.status = EventoAvaliacaoRequestStatus.PENDING;
        this.attempts = 0;
        this.availableAt = availableAt == null ? Instant.now() : availableAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public Medicao getMedicao() { return medicao; }
    public EventoAvaliacaoRequestStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Instant getProcessedAt() { return processedAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
