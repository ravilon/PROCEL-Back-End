package com.procel.ingestion.repository.rooms;

import org.springframework.data.jpa.repository.JpaRepository;
import com.procel.ingestion.entity.rooms.Unidade;
import java.util.Optional;


public interface UnidadeRepository extends JpaRepository<Unidade, Long> {
    Optional<Unidade> findByNome(String nome);
}