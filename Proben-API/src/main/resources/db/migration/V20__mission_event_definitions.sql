create table evento_definicao (
    id uuid not null default gen_random_uuid(),
    missao_id uuid not null,
    nome varchar(160) not null,
    descricao varchar(1000),
    tipo_disparo varchar(40) not null
        check (tipo_disparo in ('MEDICAO_RECEBIDA', 'AULA_INICIADA', 'AULA_ENCERRADA', 'JANELA_ENCERRADA', 'AGENDADO')),
    modo_avaliacao varchar(40) not null
        check (modo_avaliacao in ('INSTANTANEO', 'TRANSICAO', 'DURACAO', 'AGREGADO', 'CONTAGEM')),
    operador_logico varchar(10) not null
        check (operador_logico in ('ALL', 'ANY')),
    politica_atribuicao varchar(60) not null
        check (politica_atribuicao in ('ALUNOS_VINCULADOS', 'ALUNOS_VINCULADOS_COM_OCUPACAO', 'CHECKIN_CONFIRMADO', 'ATIVADOR_DA_MISSAO', 'SEM_ATRIBUICAO_AUTOMATICA')),
    janela_segundos integer,
    duracao_minima_segundos integer,
    quantidade_necessaria integer not null,
    cooldown_segundos integer,
    ordem integer not null,
    ativo boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_evento_definicao_missao foreign key (missao_id) references missao (id),
    constraint ck_evento_definicao_quantidade check (quantidade_necessaria >= 1),
    constraint ck_evento_definicao_janela check (janela_segundos is null or janela_segundos >= 0),
    constraint ck_evento_definicao_duracao check (duracao_minima_segundos is null or duracao_minima_segundos >= 0),
    constraint ck_evento_definicao_cooldown check (cooldown_segundos is null or cooldown_segundos >= 0)
);

create index ix_evento_definicao_missao_ordem
    on evento_definicao (missao_id, ordem);

create index ix_evento_definicao_ativo
    on evento_definicao (ativo);

create table evento_condicao (
    id uuid not null default gen_random_uuid(),
    evento_definicao_id uuid not null,
    parametro_def_id uuid not null,
    operador varchar(30) not null
        check (operador in ('GT', 'GTE', 'LT', 'LTE', 'EQ', 'NEQ', 'BETWEEN', 'OUTSIDE', 'CONTAINS')),
    valor_numeric_1 numeric(18,6),
    valor_numeric_2 numeric(18,6),
    valor_boolean boolean,
    valor_text varchar(1000),
    agregacao varchar(40) not null
        check (agregacao in ('ULTIMO', 'PRIMEIRO', 'MIN', 'MAX', 'MEDIA', 'SOMA', 'CONTAGEM', 'TEMPO_VERDADEIRO', 'DELTA')),
    obrigatoria boolean not null,
    ordem integer not null,
    ativo boolean not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_evento_condicao_evento foreign key (evento_definicao_id) references evento_definicao (id),
    constraint fk_evento_condicao_parametro foreign key (parametro_def_id) references parametro_def (id)
);

create unique index uk_evento_condicao_evento_ordem_ativo
    on evento_condicao (evento_definicao_id, ordem)
    where ativo;

create index ix_evento_condicao_evento_ordem
    on evento_condicao (evento_definicao_id, ordem);

create index ix_evento_condicao_parametro
    on evento_condicao (parametro_def_id);
