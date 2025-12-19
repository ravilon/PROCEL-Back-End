
-- =====================================================
-- PROCEL - Consultas Úteis (Exemplos)
-- Schema: campus, predio, unidade, compartimento
-- =====================================================

-- 1) Mapa hierárquico completo
SELECT
  c.nome AS campus,
  p.nome AS predio,
  u.nome AS unidade,
  co.nome AS compartimento,
  co.tipo,
  co.pavimento,
  co.capacidade,
  co.area
FROM compartimento co
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
JOIN unidade u ON u.id = co.unidadeid
ORDER BY c.nome, p.nome, co.tipo, co.nome;


-- 2) Contagem de compartimentos por campus
SELECT
  c.nome AS campus,
  COUNT(*) AS qtd_compartimentos
FROM compartimento co
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
GROUP BY c.nome
ORDER BY qtd_compartimentos DESC, c.nome;


-- 3) Contagem de compartimentos por prédio (Top 20)
SELECT
  c.nome AS campus,
  p.nome AS predio,
  COUNT(*) AS qtd_compartimentos
FROM compartimento co
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
GROUP BY c.nome, p.nome
ORDER BY qtd_compartimentos DESC
LIMIT 20;


-- 4) Distribuição por tipo de compartimento
SELECT
  co.tipo,
  COUNT(*) AS qtd
FROM compartimento co
GROUP BY co.tipo
ORDER BY qtd DESC, co.tipo;


-- 5) Salas de aula com capacidade
SELECT
  c.nome AS campus,
  p.nome AS predio,
  co.nome AS sala,
  co.pavimento,
  co.capacidade,
  co.area
FROM compartimento co
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
WHERE co.tipo = 'Sala de Aula'
ORDER BY c.nome, p.nome, co.capacidade DESC NULLS LAST, co.nome;


-- 6) Maiores compartimentos por área (Top 30)
SELECT
  c.nome AS campus,
  p.nome AS predio,
  co.nome AS compartimento,
  co.tipo,
  co.area
FROM compartimento co
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
WHERE co.area IS NOT NULL
ORDER BY co.area DESC
LIMIT 30;


-- 7) Compartimentos por unidade
SELECT
  u.nome AS unidade,
  COUNT(*) AS qtd_compartimentos
FROM compartimento co
JOIN unidade u ON u.id = co.unidadeid
GROUP BY u.nome
ORDER BY qtd_compartimentos DESC, u.nome;


-- 8) Compartimentos por unidade e tipo
SELECT
  u.nome AS unidade,
  co.tipo,
  COUNT(*) AS qtd
FROM compartimento co
JOIN unidade u ON u.id = co.unidadeid
GROUP BY u.nome, co.tipo
ORDER BY u.nome, qtd DESC;


-- 9) Ranking de capacidade (Top 50)
SELECT
  c.nome AS campus,
  p.nome AS predio,
  co.nome AS compartimento,
  co.tipo,
  co.capacidade
FROM compartimento co
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
WHERE co.capacidade IS NOT NULL
ORDER BY co.capacidade DESC
LIMIT 50;


-- 10) Filtro operacional (exemplo)
SELECT
  p.nome AS predio,
  co.nome AS compartimento,
  co.capacidade,
  co.area
FROM compartimento co
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
WHERE c.nome = 'Campus Anglo'
  AND co.tipo = 'Sala de Aula'
  AND co.pavimento = 3
ORDER BY co.nome;


-- 11) View para consumo em API / backend
CREATE OR REPLACE VIEW vw_compartimentos AS
SELECT
  co.id AS compartimentoid,
  co.nome AS compartimento,
  co.tipo,
  co.pavimento,
  co.capacidade,
  co.area,
  p.id AS predioid,
  p.nome AS predio,
  c.id AS campusid,
  c.nome AS campus,
  u.id AS unidadeid,
  u.nome AS unidade
FROM compartimento co
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
JOIN unidade u ON u.id = co.unidadeid;
