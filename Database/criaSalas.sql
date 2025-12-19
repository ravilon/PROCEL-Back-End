BEGIN;

CREATE TABLE campus (
  id    BIGSERIAL PRIMARY KEY,
  nome  VARCHAR(150) NOT NULL
);

CREATE TABLE predio (
  id        BIGSERIAL PRIMARY KEY,
  campusId  BIGINT NOT NULL
            REFERENCES campus(id)
            ON UPDATE CASCADE
            ON DELETE RESTRICT,
  nome      VARCHAR(150) NOT NULL
);

CREATE TABLE unidade (
  id    BIGSERIAL PRIMARY KEY,
  nome  VARCHAR(150) NOT NULL
);

CREATE TABLE compartimento (
  id           BIGSERIAL PRIMARY KEY,
  predioId     BIGINT NOT NULL
               REFERENCES predio(id)
               ON UPDATE CASCADE
               ON DELETE RESTRICT,
  unidadeId    BIGINT NOT NULL
               REFERENCES unidade(id)
               ON UPDATE CASCADE
               ON DELETE RESTRICT,

  nome         VARCHAR(150) NOT NULL,
  tipo         VARCHAR(80)  NOT NULL,
  pavimento    INTEGER,
  capacidade   INTEGER CHECK (capacidade IS NULL OR capacidade >= 0),
  area         NUMERIC(10,2) CHECK (area IS NULL OR area >= 0)
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_predio_campus
  ON predio (campusId);

CREATE INDEX IF NOT EXISTS idx_compartimento_predio
  ON compartimento (predioId);

CREATE INDEX IF NOT EXISTS idx_compartimento_unidade
  ON compartimento (unidadeId);

-- Únicos (opcionais)
CREATE UNIQUE INDEX IF NOT EXISTS ux_predio_nome_por_campus
  ON predio (campusId, nome);

CREATE UNIQUE INDEX IF NOT EXISTS ux_compartimento_nome_por_predio
  ON compartimento (predioId, nome);

COMMIT;
