package com.procel.ingestion.entity.people;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "pessoa",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pessoa_email", columnNames = {"email"}),
                @UniqueConstraint(name = "uk_pessoa_matricula", columnNames = {"matricula"})
        },
        indexes = {
                @Index(name = "ix_pessoa_email", columnList = "email"),
                @Index(name = "ix_pessoa_matricula", columnList = "matricula")
        }
)
public class Pessoa {

    @Id
    @Column(name = "id", nullable = false, length = 80)
    private String id; // userId (PK)

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "email", nullable = false, length = 200)
    private String email;

    // password == password_hash
    @Column(name = "password", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "telefone", length = 40)
    private String telefone;

    @Column(name = "matricula", length = 80)
    private String matricula;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "pessoa_role",
            joinColumns = @JoinColumn(name = "pessoa_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 40)
    private Set<Role> roles = new HashSet<>();

    protected Pessoa() {}

    public Pessoa(String id, String nome, String email, String passwordHash, String telefone, String matricula) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.passwordHash = passwordHash;
        this.telefone = telefone;
        this.matricula = matricula;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getTelefone() { return telefone; }
    public String getMatricula() { return matricula; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<Role> getRoles() { return roles; }

    public void setNome(String nome) { this.nome = nome; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public void setMatricula(String matricula) { this.matricula = matricula; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}
