package com.procel.ingestion.entity.people;

import com.procel.ingestion.entity.rooms.Compartimento;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "presenca",
    indexes = {
        @Index(name = "ix_presenca_pessoa_checkout", columnList = "pessoa_id, checkout_at"),
        @Index(name = "ix_presenca_compartimento_checkout", columnList = "compartimento_id, checkout_at"),
        @Index(name = "ix_presenca_checkin", columnList = "checkin_at")
    }
)
public class Presenca {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false, foreignKey = @ForeignKey(name = "fk_presenca_pessoa"))
    private Pessoa pessoa;

    // Compartimento PK é String
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "compartimento_id", nullable = false, foreignKey = @ForeignKey(name = "fk_presenca_compartimento"))
    private Compartimento compartimento;

    @Column(name = "checkin_at", nullable = false)
    private Instant checkinAt;

    @Column(name = "checkout_at")
    private Instant checkoutAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "source", length = 60)
    private String source;

    protected Presenca() {}

    public Presenca(Pessoa pessoa, Compartimento compartimento, Instant checkinAt, String source) {
        this.pessoa = pessoa;
        this.compartimento = compartimento;
        this.checkinAt = checkinAt;
        this.source = source;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Pessoa getPessoa() { return pessoa; }
    public Compartimento getCompartimento() { return compartimento; }
    public Instant getCheckinAt() { return checkinAt; }
    public Instant getCheckoutAt() { return checkoutAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSource() { return source; }

    public boolean isOpen() { return checkoutAt == null; }

    public void checkout(Instant checkoutAt) {
        this.checkoutAt = checkoutAt;
    }
}