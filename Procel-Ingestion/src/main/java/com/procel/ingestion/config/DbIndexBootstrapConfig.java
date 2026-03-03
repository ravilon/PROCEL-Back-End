package com.procel.ingestion.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * MVP: aplica índices/constraints não suportados por JPA annotations.
 * Quando entrar Flyway: mover a DDL para migration e remover esta classe.
 */
@Component
public class DbIndexBootstrapConfig implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DbIndexBootstrapConfig(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // Garante no máximo 1 sessão de presença ABERTA por pessoa (checkout_at IS NULL)
        jdbc.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_presenca_open_by_pessoa
            ON presenca (pessoa_id)
            WHERE checkout_at IS NULL
        """);

        // (Opcional) acelera ocupação por compartimento (abertas)
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS ix_presenca_open_by_compartimento
            ON presenca (compartimento_id)
            WHERE checkout_at IS NULL
        """);
    }
}