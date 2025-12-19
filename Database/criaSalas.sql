BEGIN;

-- (Opcional) se você quiser recriar do zero:
-- DROP TABLE IF EXISTS compartimento;
-- DROP TABLE IF EXISTS predio;
-- DROP TABLE IF EXISTS unidade;
-- DROP TABLE IF EXISTS campus;

CREATE TABLE IF NOT EXISTS campus (
    id    BIGSERIAL PRIMARY KEY,
    nome  VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS predio (
    id        BIGSERIAL PRIMARY KEY,
    campusid  BIGINT NOT NULL
              REFERENCES campus(id)
              ON UPDATE CASCADE
              ON DELETE RESTRICT,
    nome      VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS unidade (
    id    BIGSERIAL PRIMARY KEY,
    nome  VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS compartimento (
    id           BIGSERIAL PRIMARY KEY,
    predioid     BIGINT NOT NULL
                 REFERENCES predio(id)
                 ON UPDATE CASCADE
                 ON DELETE RESTRICT,
    unidadeid    BIGINT NOT NULL
                 REFERENCES unidade(id)
                 ON UPDATE CASCADE
                 ON DELETE RESTRICT,

    nome         VARCHAR(150) NOT NULL,
    tipo         VARCHAR(80)  NOT NULL,
    pavimento    INTEGER,
    capacidade   INTEGER CHECK (capacidade IS NULL OR capacidade >= 0),
    area         NUMERIC(10,2) CHECK (area IS NULL OR area >= 0)
);

-- =========================
-- Índices (FKs)
-- =========================
CREATE INDEX IF NOT EXISTS idx_predio_campusid
    ON predio (campusid);

CREATE INDEX IF NOT EXISTS idx_compartimento_predioid
    ON compartimento (predioid);

CREATE INDEX IF NOT EXISTS idx_compartimento_unidadeid
    ON compartimento (unidadeid);

-- =========================
-- Unicidade (necessária p/ ON CONFLICT)
-- =========================
CREATE UNIQUE INDEX IF NOT EXISTS ux_campus_nome
    ON campus (nome);

CREATE UNIQUE INDEX IF NOT EXISTS ux_unidade_nome
    ON unidade (nome);

CREATE UNIQUE INDEX IF NOT EXISTS ux_predio_nome_campus
    ON predio (campusid, nome);

CREATE UNIQUE INDEX IF NOT EXISTS ux_compartimento_predioid_nome
    ON compartimento (predioid, nome);

COMMIT;
