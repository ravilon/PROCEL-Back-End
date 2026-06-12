
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


-- =====================================================
-- OCORRENCIAS DE AULA
-- "periodo_aula" representa a posicao da aula dentro do turno.
-- =====================================================

-- 12) Agenda completa de ocorrencias por intervalo de datas
SELECT
  oa.id,
  oa.data,
  oa.turno,
  oa.periodo_aula,
  oa.hora_inicio,
  oa.hora_fim,
  oa.tipo,
  oa.turma,
  d.id AS disciplina_id,
  d.nome AS disciplina,
  d.unidade_sigla,
  co.id AS compartimento_id,
  co.nome AS compartimento,
  p.nome AS predio,
  c.nome AS campus,
  oa.descricao,
  oa.source,
  oa.sincronizado_em
FROM ocorrencia_aula oa
JOIN compartimento co ON co.id = oa.compartimento_id
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
LEFT JOIN disciplina d ON d.id = oa.disciplina_id
WHERE oa.data BETWEEN DATE '2026-06-07' AND DATE '2026-06-13'
ORDER BY oa.data, oa.hora_inicio, c.nome, p.nome, co.nome;


-- 13) Agenda de uma sala em uma data
SELECT
  oa.data,
  oa.turno,
  oa.periodo_aula,
  oa.hora_inicio,
  oa.hora_fim,
  oa.tipo,
  oa.turma,
  COALESCE(d.nome, oa.descricao) AS atividade
FROM ocorrencia_aula oa
LEFT JOIN disciplina d ON d.id = oa.disciplina_id
WHERE oa.compartimento_id = '1000'
  AND oa.data = DATE '2026-06-11'
ORDER BY oa.turno, oa.periodo_aula, oa.hora_inicio;


-- 14) Agenda de uma disciplina
SELECT
  d.id AS disciplina_id,
  d.nome AS disciplina,
  oa.turma,
  oa.data,
  oa.turno,
  oa.periodo_aula,
  oa.hora_inicio,
  oa.hora_fim,
  co.id AS compartimento_id,
  co.nome AS compartimento,
  p.nome AS predio
FROM ocorrencia_aula oa
JOIN disciplina d ON d.id = oa.disciplina_id
JOIN compartimento co ON co.id = oa.compartimento_id
JOIN predio p ON p.id = co.predioid
WHERE d.id = 27064
  AND oa.data BETWEEN DATE '2026-06-07' AND DATE '2026-06-13'
ORDER BY oa.data, oa.turno, oa.periodo_aula;


-- 15) Quantidade de ocorrencias por tipo e data
SELECT
  oa.data,
  oa.tipo,
  COUNT(*) AS qtd_ocorrencias
FROM ocorrencia_aula oa
WHERE oa.data BETWEEN DATE '2026-06-07' AND DATE '2026-06-13'
GROUP BY oa.data, oa.tipo
ORDER BY oa.data, oa.tipo;


-- 16) Salas com maior quantidade de periodos ocupados
SELECT
  c.nome AS campus,
  p.nome AS predio,
  co.id AS compartimento_id,
  co.nome AS compartimento,
  COUNT(*) AS periodos_ocupados,
  COUNT(DISTINCT oa.data) AS dias_com_ocorrencia
FROM ocorrencia_aula oa
JOIN compartimento co ON co.id = oa.compartimento_id
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
WHERE oa.data BETWEEN DATE '2026-06-07' AND DATE '2026-06-13'
GROUP BY c.nome, p.nome, co.id, co.nome
ORDER BY periodos_ocupados DESC, c.nome, p.nome, co.nome;


-- 17) Possiveis conflitos de horario na mesma sala
SELECT
  oa1.compartimento_id,
  co.nome AS compartimento,
  oa1.data,
  oa1.id AS ocorrencia_1,
  oa2.id AS ocorrencia_2,
  oa1.hora_inicio AS inicio_1,
  oa1.hora_fim AS fim_1,
  oa2.hora_inicio AS inicio_2,
  oa2.hora_fim AS fim_2
FROM ocorrencia_aula oa1
JOIN ocorrencia_aula oa2
  ON oa2.compartimento_id = oa1.compartimento_id
 AND oa2.data = oa1.data
 AND oa2.id > oa1.id
 AND oa1.hora_inicio < oa2.hora_fim
 AND oa2.hora_inicio < oa1.hora_fim
JOIN compartimento co ON co.id = oa1.compartimento_id
ORDER BY oa1.data, co.nome, oa1.hora_inicio;


-- 18) Ocorrencias sem disciplina associada
-- Inclui provas, reservas e outros registros do Cobalto sem disciplina.
SELECT
  oa.id,
  oa.data,
  oa.turno,
  oa.periodo_aula,
  oa.hora_inicio,
  oa.hora_fim,
  oa.tipo,
  oa.descricao,
  co.id AS compartimento_id,
  co.nome AS compartimento
FROM ocorrencia_aula oa
JOIN compartimento co ON co.id = oa.compartimento_id
WHERE oa.disciplina_id IS NULL
ORDER BY oa.data DESC, oa.hora_inicio, co.nome;


-- 19) Disciplinas com quantidade de turmas e ocorrencias
SELECT
  d.id AS disciplina_id,
  d.nome AS disciplina,
  d.unidade_sigla,
  COUNT(DISTINCT oa.turma) AS qtd_turmas,
  COUNT(oa.id) AS qtd_ocorrencias,
  MIN(oa.data) AS primeira_ocorrencia,
  MAX(oa.data) AS ultima_ocorrencia
FROM disciplina d
LEFT JOIN ocorrencia_aula oa ON oa.disciplina_id = d.id
GROUP BY d.id, d.nome, d.unidade_sigla
ORDER BY qtd_ocorrencias DESC, d.nome;


-- 20) Ultima sincronizacao das ocorrencias por sala
SELECT
  co.id AS compartimento_id,
  co.nome AS compartimento,
  MAX(oa.sincronizado_em) AS ultima_sincronizacao,
  COUNT(oa.id) AS qtd_ocorrencias
FROM compartimento co
LEFT JOIN ocorrencia_aula oa ON oa.compartimento_id = co.id
GROUP BY co.id, co.nome
ORDER BY ultima_sincronizacao DESC NULLS LAST, co.nome;


-- 21) View consolidada de ocorrencias de aula
CREATE OR REPLACE VIEW vw_ocorrencias_aula AS
SELECT
  oa.id,
  oa.data,
  oa.turno,
  oa.periodo_aula,
  oa.hora_inicio,
  oa.hora_fim,
  oa.tipo,
  oa.turma,
  oa.descricao,
  oa.source,
  oa.sincronizado_em,
  d.id AS disciplina_id,
  d.nome AS disciplina,
  d.unidade_sigla,
  co.id AS compartimento_id,
  co.nome AS compartimento,
  co.tipo AS compartimento_tipo,
  p.id AS predio_id,
  p.nome AS predio,
  c.id AS campus_id,
  c.nome AS campus,
  u.id AS unidade_id,
  u.nome AS unidade
FROM ocorrencia_aula oa
JOIN compartimento co ON co.id = oa.compartimento_id
JOIN predio p ON p.id = co.predioid
JOIN campus c ON c.id = p.campusid
JOIN unidade u ON u.id = co.unidadeid
LEFT JOIN disciplina d ON d.id = oa.disciplina_id;
