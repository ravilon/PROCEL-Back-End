-- Sensor rule tables previously relied on Hibernate ddl-auto=update.
create table if not exists grupo_regra (
    id uuid primary key,
    nome varchar(160) not null,
    descricao varchar(500),
    ativo boolean not null,
    created_at timestamptz not null
);
create table if not exists regra_parametro (
    id uuid primary key,
    grupo_regra_id uuid not null references grupo_regra(id),
    parametro_def_id uuid not null references parametro_def(id),
    nome varchar(160) not null,
    descricao varchar(500),
    operador varchar(30) not null,
    valor_numeric_1 numeric(18,6),
    valor_numeric_2 numeric(18,6),
    valor_text varchar(1000),
    valor_boolean boolean,
    resultado varchar(30) not null,
    severidade integer not null,
    prioridade integer not null,
    ativo boolean not null,
    created_at timestamptz not null
);
create table if not exists sensor_grupo_regra (
    id uuid primary key,
    sensor_external_id varchar(120) not null references sensor(external_id),
    grupo_regra_id uuid not null references grupo_regra(id),
    status varchar(30) not null,
    valido_de timestamptz,
    valido_ate timestamptz,
    created_at timestamptz not null
);
create table if not exists avaliacao_parametro_valor (
    id uuid primary key,
    parametro_valor_id uuid not null references parametro_valor(id),
    regra_parametro_id uuid references regra_parametro(id),
    resultado varchar(30) not null,
    severidade integer not null,
    mensagem varchar(500),
    avaliado_em timestamptz not null,
    constraint ux_avaliacao_valor_regra unique (parametro_valor_id, regra_parametro_id)
);
create index if not exists idx_regra_parametro_grupo_param on regra_parametro(grupo_regra_id, parametro_def_id);
create index if not exists idx_regra_parametro_ativo on regra_parametro(ativo);
create index if not exists idx_sensor_grupo_regra_lookup on sensor_grupo_regra(sensor_external_id, status, valido_de, valido_ate);
create index if not exists idx_sensor_grupo_regra_grupo on sensor_grupo_regra(grupo_regra_id);
create index if not exists idx_avaliacao_parametro_valor on avaliacao_parametro_valor(parametro_valor_id);
create index if not exists idx_avaliacao_regra_parametro on avaliacao_parametro_valor(regra_parametro_id);
create index if not exists idx_avaliacao_resultado on avaliacao_parametro_valor(resultado);
alter table evento_definicao
    add column papel varchar(20) not null default 'PROGRESSO',
    add column lacuna_maxima_segundos integer;

alter table evento_definicao
    add constraint ck_evento_definicao_papel
    check (papel in ('ATRIBUICAO', 'PROGRESSO', 'CONCLUSAO')),
    add constraint ck_evento_definicao_lacuna
    check (lacuna_maxima_segundos is null or lacuna_maxima_segundos > 0);

alter table evento_condicao
    add column fonte varchar(20) not null default 'PARAMETRO_VALOR',
    add column regra_parametro_id uuid,
    add column resultado_esperado varchar(30);

alter table evento_condicao
    add constraint ck_evento_condicao_fonte
    check (fonte in ('PARAMETRO_VALOR', 'AVALIACAO_REGRA')),
    add constraint fk_evento_condicao_regra
    foreign key (regra_parametro_id) references regra_parametro(id),
    add constraint ck_evento_condicao_regra_fonte
    check ((fonte = 'PARAMETRO_VALOR' and regra_parametro_id is null and resultado_esperado is null)
        or (fonte = 'AVALIACAO_REGRA' and regra_parametro_id is not null and resultado_esperado is not null));

create index ix_evento_condicao_regra on evento_condicao(regra_parametro_id)
    where regra_parametro_id is not null;

alter table atividade
    add column compartimento_id varchar(80),
    add column periodo_aula_id uuid,
    add column atribuicao_ocorrencia_id uuid,
    add column parent_atividade_id uuid;

alter table atividade
    add constraint fk_atividade_compartimento foreign key (compartimento_id) references compartimento(id),
    add constraint fk_atividade_periodo_aula foreign key (periodo_aula_id) references periodo_aula(id),
    add constraint fk_atividade_atribuicao_ocorrencia foreign key (atribuicao_ocorrencia_id) references evento_ocorrencia(id),
    add constraint fk_atividade_parent_atividade foreign key (parent_atividade_id) references atividade(id);

create index ix_atividade_correlacao
    on atividade(missao_id, compartimento_id, periodo_aula_id, chave_ciclo, status);

create index ix_atividade_parent_atividade on atividade(parent_atividade_id);

alter table evento_ocorrencia_evidencia
    add column avaliacao_parametro_valor_id uuid,
    add constraint fk_evento_evidencia_avaliacao
    foreign key (avaliacao_parametro_valor_id) references avaliacao_parametro_valor(id);
