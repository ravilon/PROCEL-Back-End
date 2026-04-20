-- PROCEL Ingestion - Parameter Qualification verification queries
--
-- Use after running:
--   .\Documentos\ApiSmokeTests\ApiSmokeTest.ps1 -Target local
--
-- Local database defaults:
--   host: localhost
--   port: 5432
--   database: procel_analytics
--   user: postgres
--   password: postgres
--
-- psql example:
--   psql -h localhost -p 5432 -U postgres -d procel_analytics -f .\Documentos\ApiSmokeTests\VerifyParameterQualification.sql
--
-- Note: application.yml currently uses ddl-auto=create-drop, so restarting
-- the app can drop/recreate these tables and remove smoke-test data.

-- 1) Rule groups created by the smoke test.
select
  id,
  nome,
  descricao,
  ativo,
  created_at
from grupo_regra
where nome like 'API test DER%'
order by created_at desc;

-- 2) Rules created for temperature_c.
select
  rp.id,
  gr.nome as grupo_nome,
  pd.nome as parametro_nome,
  pd.data_type,
  rp.nome as regra_nome,
  rp.operador,
  rp.valor_numeric_1,
  rp.valor_numeric_2,
  rp.resultado,
  rp.severidade,
  rp.prioridade,
  rp.ativo,
  rp.created_at
from regra_parametro rp
join grupo_regra gr on gr.id = rp.grupo_regra_id
join parametro_def pd on pd.id = rp.parametro_def_id
where gr.nome like 'API test DER%'
order by rp.created_at desc;

-- 3) Active rule-group links for the test sensor.
select
  sgr.id,
  sgr.sensor_external_id,
  gr.nome as grupo_nome,
  sgr.status,
  sgr.valido_de,
  sgr.valido_ate,
  sgr.created_at
from sensor_grupo_regra sgr
join grupo_regra gr on gr.id = sgr.grupo_regra_id
where sgr.sensor_external_id = 'SII-001'
  and gr.nome like 'API test DER%'
order by sgr.created_at desc;

-- 4) Recent smoke-test measurements for the test sensor.
select
  id,
  sensor_external_id,
  timestamp,
  recebido_em,
  source
from medicao
where sensor_external_id = 'SII-001'
order by timestamp desc
limit 10;

-- 5) Recent temperature_c parameter values.
select
  m.id as medicao_id,
  m.timestamp,
  m.source,
  pv.id as parametro_valor_id,
  pd.nome as parametro_nome,
  pv.numeric_value
from medicao m
join parametro_valor pv on pv.medicao_id = m.id
join parametro_def pd on pd.id = pv.parametro_def_id
where m.sensor_external_id = 'SII-001'
  and pd.nome = 'temperature_c'
order by m.timestamp desc
limit 20;

-- 6) Parameter Qualification rows generated for temperature_c.
select
  apv.id as avaliacao_id,
  m.timestamp,
  m.source,
  pd.nome as parametro_nome,
  pv.numeric_value,
  rp.nome as regra_nome,
  rp.operador,
  rp.valor_numeric_1,
  apv.resultado,
  apv.severidade,
  apv.mensagem,
  apv.avaliado_em
from avaliacao_parametro_valor apv
join parametro_valor pv on pv.id = apv.parametro_valor_id
join medicao m on m.id = pv.medicao_id
join parametro_def pd on pd.id = pv.parametro_def_id
left join regra_parametro rp on rp.id = apv.regra_parametro_id
where m.sensor_external_id = 'SII-001'
  and pd.nome = 'temperature_c'
order by m.timestamp desc
limit 20;

-- 7) Evaluation totals by parameter/rule/result for smoke-test measurements.
select
  pd.nome as parametro_nome,
  rp.nome as regra_nome,
  apv.resultado,
  count(*) as total
from avaliacao_parametro_valor apv
join parametro_valor pv on pv.id = apv.parametro_valor_id
join parametro_def pd on pd.id = pv.parametro_def_id
join regra_parametro rp on rp.id = apv.regra_parametro_id
join medicao m on m.id = pv.medicao_id
where m.sensor_external_id = 'SII-001'
  and m.source = 'api-test'
group by pd.nome, rp.nome, apv.resultado
order by total desc;
