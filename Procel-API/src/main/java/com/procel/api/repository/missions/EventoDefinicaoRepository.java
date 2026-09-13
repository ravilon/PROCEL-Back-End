package com.procel.api.repository.missions;

import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoTipoDisparo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventoDefinicaoRepository extends JpaRepository<EventoDefinicao, UUID> {
    List<EventoDefinicao> findByMissaoIdOrderByOrdemAscCreatedAtAsc(UUID missaoId);

    @Query("""
           select distinct e
           from EventoDefinicao e
           join fetch e.missao m
           left join fetch e.condicoes c
           left join fetch c.parametroDef pd
           where e.ativo = true
             and m.ativo = true
             and e.tipoDisparo = :tipoDisparo
             and e.modoAvaliacao = :modoAvaliacao
           order by e.ordem asc, e.createdAt asc
           """)
    List<EventoDefinicao> findActiveInstantMeasurementEvents(
            @Param("tipoDisparo") EventoTipoDisparo tipoDisparo,
            @Param("modoAvaliacao") EventoModoAvaliacao modoAvaliacao
    );
}
