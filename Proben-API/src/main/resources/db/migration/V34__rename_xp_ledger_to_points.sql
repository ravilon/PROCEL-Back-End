-- Keep the applied V23 and V33 migrations immutable; this migration renames the live schema.
alter table missao rename column value to pontos;

alter table xp_lancamento rename to pontos_lancamento;
alter table pontos_lancamento rename column quantidade to pontos;

alter table pontos_lancamento rename constraint fk_xp_lancamento_pessoa to fk_pontos_lancamento_pessoa;
alter table pontos_lancamento rename constraint fk_xp_lancamento_atividade to fk_pontos_lancamento_atividade;
alter table pontos_lancamento rename constraint fk_xp_lancamento_missao to fk_pontos_lancamento_missao;
alter table pontos_lancamento rename constraint fk_xp_lancamento_evento_ocorrencia to fk_pontos_lancamento_evento_ocorrencia;
alter table pontos_lancamento rename constraint uk_xp_lancamento_chave_idempotencia to uk_pontos_lancamento_chave_idempotencia;
alter table pontos_lancamento rename constraint ck_xp_lancamento_tipo to ck_pontos_lancamento_tipo;
alter table pontos_lancamento rename constraint ck_xp_lancamento_quantidade_nonzero to ck_pontos_lancamento_pontos_nonzero;
alter table pontos_lancamento rename constraint ck_xp_lancamento_tipo_quantidade to ck_pontos_lancamento_tipo_pontos;
alter table pontos_lancamento rename constraint fk_xp_regra_ambiente_execucao to fk_pontos_regra_ambiente_execucao;
alter table pontos_lancamento rename constraint fk_xp_regra_ambiente_compartimento to fk_pontos_regra_ambiente_compartimento;
alter table pontos_lancamento rename constraint ck_xp_lancamento_origem to ck_pontos_lancamento_origem;
alter table pontos_lancamento rename constraint ck_xp_lancamento_origem_links to ck_pontos_lancamento_origem_links;

alter index ux_xp_lancamento_auto_completion_atividade rename to ux_pontos_lancamento_auto_completion_atividade;
alter index ix_xp_lancamento_pessoa_created_at rename to ix_pontos_lancamento_pessoa_created_at;
alter index ix_xp_lancamento_atividade rename to ix_pontos_lancamento_atividade;
alter index ix_xp_lancamento_evento_ocorrencia rename to ix_pontos_lancamento_evento_ocorrencia;
alter index ux_xp_lancamento_environment_beneficiary rename to ux_pontos_lancamento_environment_beneficiary;
alter index ix_xp_lancamento_environment_execution rename to ix_pontos_lancamento_environment_execution;

update pontos_lancamento
set chave_idempotencia = 'pontos:atividade:' || substr(chave_idempotencia, length('xp:activity:') + 1)
where chave_idempotencia like 'xp:activity:%';

update pontos_lancamento
set chave_idempotencia = 'pontos:ambiente:' || substr(chave_idempotencia, length('xp:environment:') + 1)
where chave_idempotencia like 'xp:environment:%';

update missao
set descricao = regexp_replace(descricao, '\mXP\M', 'pontos', 'gi')
where descricao ~* '\mXP\M';
