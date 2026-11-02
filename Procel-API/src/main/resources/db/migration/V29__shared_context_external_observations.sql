create table contexto_compartilhado (
    id uuid primary key,
    nome varchar(160) not null,
    descricao varchar(1000),
    ativo boolean not null default true,
    latitude numeric(9,6),
    longitude numeric(9,6),
    timezone varchar(80) not null default 'America/Sao_Paulo',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_contexto_coordinates check (
        (latitude is null and longitude is null) or
        (latitude between -90 and 90 and longitude between -180 and 180)
    )
);

create table contexto_compartilhado_compartimento (
    contexto_id uuid not null references contexto_compartilhado(id) on delete cascade,
    compartimento_id varchar(80) not null references compartimento(id) on delete cascade,
    ativo boolean not null default true,
    created_at timestamptz not null default now(),
    primary key (contexto_id, compartimento_id)
);
create index idx_contexto_room_active on contexto_compartilhado_compartimento(compartimento_id, ativo);

alter table sensor add column contexto_compartilhado_id uuid references contexto_compartilhado(id);
alter table sensor alter column compartimento_id drop not null;
alter table sensor add constraint ck_sensor_exactly_one_scope check (
    (compartimento_id is not null and contexto_compartilhado_id is null) or
    (compartimento_id is null and contexto_compartilhado_id is not null)
);
create index idx_sensor_contexto on sensor(contexto_compartilhado_id);

create table contexto_fonte_selecao (
    compartimento_id varchar(80) not null references compartimento(id) on delete cascade,
    parametro_def_id uuid not null references parametro_def(id),
    sensor_external_id varchar(120) not null references sensor(external_id),
    validade_segundos integer not null default 3600,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (compartimento_id, parametro_def_id),
    constraint ck_fonte_validade check (validade_segundos > 0)
);

create table contexto_integracao_externa (
    id uuid primary key,
    contexto_id uuid not null references contexto_compartilhado(id) on delete cascade,
    sensor_external_id varchar(120) not null references sensor(external_id),
    nome varchar(160) not null,
    ativo boolean not null default true,
    endpoint_url varchar(1000) not null,
    metodo varchar(8) not null default 'GET',
    request_body varchar(4000),
    credential_env varchar(160),
    credential_header varchar(120),
    temperature_json_pointer varchar(500) not null,
    observed_at_json_pointer varchar(500),
    source_id_json_pointer varchar(500),
    interval_seconds integer not null default 900,
    timeout_millis integer not null default 5000,
    max_attempts integer not null default 2,
    next_run_at timestamptz not null default now(),
    locked_until timestamptz,
    locked_by varchar(160),
    last_started_at timestamptz,
    last_success_at timestamptz,
    last_error varchar(1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ux_contexto_integracao_sensor unique(sensor_external_id),
    constraint ck_contexto_integracao_limits check (
        metodo in ('GET','POST') and interval_seconds >= 60 and
        timeout_millis between 100 and 60000 and max_attempts between 1 and 5
    )
);
create index idx_contexto_integracao_due on contexto_integracao_externa(ativo, next_run_at, locked_until);

comment on table contexto_compartilhado is 'Cobertura compartilhada e localizacao cadastrada, independente de grupos de regra e do Cobalto.';
comment on column contexto_integracao_externa.credential_env is 'Nome da variavel de ambiente que contem a credencial; o segredo nao e persistido.';
