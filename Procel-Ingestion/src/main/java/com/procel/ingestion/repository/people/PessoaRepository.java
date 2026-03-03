package com.procel.ingestion.repository.people;

import com.procel.ingestion.entity.people.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PessoaRepository extends JpaRepository<Pessoa, String> {
    Optional<Pessoa> findByEmail(String email);
    Optional<Pessoa> findByMatricula(String matricula);
    boolean existsByEmail(String email);
    boolean existsByMatricula(String matricula);
}