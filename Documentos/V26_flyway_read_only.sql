-- Auditoria read-only da V26.
-- Executar separadamente, com uma conexao somente leitura, em local, staging
-- e producao. Este arquivo nao contem DDL, DML, flyway repair ou credenciais.
--
-- Decisao pre-deploy registrada em 2026-09-18:
-- nao foi encontrada evidencia local de V26 aplicada (o banco persistente
-- local nao possui flyway_schema_history). Nao houve acesso a staging ou
-- producao. A V26 corrigida deve permanecer antes do deploy.
--
-- O checksum e calculado pelo Flyway sobre o conteudo normalizado da migration.
-- V26 original no commit 0ab2393: -2046449006
-- V26 corrigida no working tree:  1732619328

-- 1. Historico e checksum da V26 por ambiente
select
    current_database() as database_name,
    current_user as database_user,
    version,
    description,
    script,
    checksum,
    installed_on,
    installed_by,
    success
from flyway_schema_history
where version = '26';

-- 2. Resumo para confirmar que nao ha V26 aplicada com falha ou sucesso
select
    count(*) as v26_history_rows,
    count(*) filter (where success) as successful_v26_rows,
    count(*) filter (where not success) as failed_v26_rows,
    min(checksum) as observed_checksum
from flyway_schema_history
where version = '26';

-- 3. Comparacao manual, sem alterar o banco.
-- Resultado esperado quando nao existe V26: v26_history_rows = 0.
-- Resultado esperado para a V26 original: observed_checksum = -2046449006.
select
    case
        when count(*) = 0 then 'NOT_APPLIED'
        when bool_and(success) and min(checksum) = -2046449006 then 'ORIGINAL_APPLIED'
        when bool_and(success) then 'DIFFERENT_CHECKSUM_APPLIED'
        else 'FAILED_HISTORY_ENTRY'
    end as v26_audit_status
from flyway_schema_history
where version = '26';

-- 4. Evidencia read-only dos dados protegidos pela V26.
-- Estas consultas devem ser executadas apenas se as tabelas existirem no
-- ambiente auditado; elas nao fazem alteracoes.
select 'missao' as table_name, count(*) as row_count from missao
union all
select 'atividade', count(*) from atividade
union all
select 'evento_ocorrencia', count(*) from evento_ocorrencia
union all
select 'xp_lancamento', count(*) from xp_lancamento;
