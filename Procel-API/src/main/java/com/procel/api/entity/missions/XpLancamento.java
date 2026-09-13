package com.procel.api.entity.missions;

import com.procel.api.entity.people.Pessoa;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "xp_lancamento",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_xp_lancamento_chave_idempotencia", columnNames = "chave_idempotencia")
        },
        indexes = {
                @Index(name = "ix_xp_lancamento_pessoa_created_at", columnList = "pessoa_id,created_at"),
                @Index(name = "ix_xp_lancamento_atividade", columnList = "atividade_id"),
                @Index(name = "ix_xp_lancamento_evento_ocorrencia", columnList = "evento_ocorrencia_id")
        }
)
public class XpLancamento {

    public static final String AUTO_COMPLETION_CREATED_BY = "SYSTEM_AUTO_COMPLETION";

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false, foreignKey = @ForeignKey(name = "fk_xp_lancamento_pessoa"))
    private Pessoa pessoa;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "atividade_id", nullable = false, foreignKey = @ForeignKey(name = "fk_xp_lancamento_atividade"))
    private Atividade atividade;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "missao_id", nullable = false, foreignKey = @ForeignKey(name = "fk_xp_lancamento_missao"))
    private Missao missao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_ocorrencia_id", foreignKey = @ForeignKey(name = "fk_xp_lancamento_evento_ocorrencia"))
    private EventoOcorrencia eventoOcorrencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private XpLancamentoTipo tipo;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @Column(name = "chave_idempotencia", nullable = false, length = 200)
    private String chaveIdempotencia;

    @Column(name = "descricao", length = 1000)
    private String descricao;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    protected XpLancamento() {}

    public XpLancamento(
            Pessoa pessoa,
            Atividade atividade,
            Missao missao,
            EventoOcorrencia eventoOcorrencia,
            XpLancamentoTipo tipo,
            int quantidade,
            String chaveIdempotencia,
            String descricao,
            Instant createdAt,
            String createdBy
    ) {
        validate(tipo, quantidade);
        this.pessoa = pessoa;
        this.atividade = atividade;
        this.missao = missao;
        this.eventoOcorrencia = eventoOcorrencia;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.chaveIdempotencia = requireText(chaveIdempotencia, "chaveIdempotencia");
        this.descricao = blankToNull(descricao);
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.createdBy = blankToNull(createdBy);
    }

    public UUID getId() { return id; }
    public Pessoa getPessoa() { return pessoa; }
    public Atividade getAtividade() { return atividade; }
    public Missao getMissao() { return missao; }
    public EventoOcorrencia getEventoOcorrencia() { return eventoOcorrencia; }
    public XpLancamentoTipo getTipo() { return tipo; }
    public int getQuantidade() { return quantidade; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public String getDescricao() { return descricao; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }

    @PreUpdate
    @PreRemove
    void preventMutation() {
        throw new UnsupportedOperationException("XpLancamento is append-only");
    }

    private static void validate(XpLancamentoTipo tipo, int quantidade) {
        if (tipo == null) throw new IllegalArgumentException("tipo is required");
        if (quantidade == 0) throw new IllegalArgumentException("quantidade must not be zero");
        if (tipo == XpLancamentoTipo.CONCESSAO && quantidade < 0) {
            throw new IllegalArgumentException("CONCESSAO requires positive quantidade");
        }
        if (tipo == XpLancamentoTipo.ESTORNO && quantidade > 0) {
            throw new IllegalArgumentException("ESTORNO requires negative quantidade");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
