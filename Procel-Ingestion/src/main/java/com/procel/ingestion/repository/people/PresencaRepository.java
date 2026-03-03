package com.procel.ingestion.repository.people;

import com.procel.ingestion.entity.people.Presenca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresencaRepository extends JpaRepository<Presenca, UUID> {

    @Query("""
           select p from Presenca p
           where p.pessoa.id = :pessoaId
             and p.checkoutAt is null
           """)
    Optional<Presenca> findOpenByPessoaId(String pessoaId);

    @Query("""
           select p from Presenca p
           where p.compartimento.id = :compartimentoId
             and p.checkoutAt is null
           """)
    List<Presenca> findOpenByCompartimentoId(String compartimentoId);
}