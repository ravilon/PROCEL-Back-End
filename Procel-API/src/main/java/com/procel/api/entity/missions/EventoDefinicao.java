package com.procel.api.entity.missions;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "evento_definicao",
        indexes = {
                @Index(name = "ix_evento_definicao_missao_ordem", columnList = "missao_id,ordem"),
                @Index(name = "ix_evento_definicao_ativo", columnList = "ativo")
        }
)
public class EventoDefinicao {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "missao_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_evento_definicao_missao")
    )
    private Missao missao;

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "descricao", length = 1000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_disparo", nullable = false, length = 40)
    private EventoTipoDisparo tipoDisparo;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_avaliacao", nullable = false, length = 40)
    private EventoModoAvaliacao modoAvaliacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "operador_logico", nullable = false, length = 10)
    private EventoOperadorLogico operadorLogico;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_atribuicao", nullable = false, length = 60)
    private EventoPoliticaAtribuicao politicaAtribuicao;

    @Column(name = "janela_segundos")
    private Integer janelaSegundos;

    @Column(name = "duracao_minima_segundos")
    private Integer duracaoMinimaSegundos;

    @Column(name = "quantidade_necessaria", nullable = false)
    private Integer quantidadeNecessaria = 1;

    @Column(name = "cooldown_segundos")
    private Integer cooldownSegundos;

    @Column(name = "ordem", nullable = false)
    private Integer ordem = 0;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public EventoDefinicao() {}

    public UUID getId() { return id; }
    public Missao getMissao() { return missao; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public EventoTipoDisparo getTipoDisparo() { return tipoDisparo; }
    public EventoModoAvaliacao getModoAvaliacao() { return modoAvaliacao; }
    public EventoOperadorLogico getOperadorLogico() { return operadorLogico; }
    public EventoPoliticaAtribuicao getPoliticaAtribuicao() { return politicaAtribuicao; }
    public Integer getJanelaSegundos() { return janelaSegundos; }
    public Integer getDuracaoMinimaSegundos() { return duracaoMinimaSegundos; }
    public Integer getQuantidadeNecessaria() { return quantidadeNecessaria; }
    public Integer getCooldownSegundos() { return cooldownSegundos; }
    public Integer getOrdem() { return ordem; }
    public boolean isAtivo() { return ativo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setMissao(Missao missao) { this.missao = missao; }
    public void setNome(String nome) { this.nome = nome; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public void setTipoDisparo(EventoTipoDisparo tipoDisparo) { this.tipoDisparo = tipoDisparo; }
    public void setModoAvaliacao(EventoModoAvaliacao modoAvaliacao) { this.modoAvaliacao = modoAvaliacao; }
    public void setOperadorLogico(EventoOperadorLogico operadorLogico) { this.operadorLogico = operadorLogico; }
    public void setPoliticaAtribuicao(EventoPoliticaAtribuicao politicaAtribuicao) { this.politicaAtribuicao = politicaAtribuicao; }
    public void setJanelaSegundos(Integer janelaSegundos) { this.janelaSegundos = janelaSegundos; }
    public void setDuracaoMinimaSegundos(Integer duracaoMinimaSegundos) { this.duracaoMinimaSegundos = duracaoMinimaSegundos; }
    public void setQuantidadeNecessaria(Integer quantidadeNecessaria) { this.quantidadeNecessaria = quantidadeNecessaria; }
    public void setCooldownSegundos(Integer cooldownSegundos) { this.cooldownSegundos = cooldownSegundos; }
    public void setOrdem(Integer ordem) { this.ordem = ordem; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
