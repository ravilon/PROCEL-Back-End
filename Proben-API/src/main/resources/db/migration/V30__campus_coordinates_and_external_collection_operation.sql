alter table campus add column latitude numeric(9,6);
alter table campus add column longitude numeric(9,6);
alter table campus add constraint ck_campus_coordinates check (
    (latitude is null and longitude is null) or
    (latitude between -90 and 90 and longitude between -180 and 180)
);

alter table contexto_compartilhado add column location_mode varchar(20) not null default 'OWN';
alter table contexto_compartilhado add column campus_nome varchar(255) references campus(nome) on update cascade;
alter table contexto_compartilhado add constraint ck_contexto_location_mode check (
    (location_mode = 'OWN' and campus_nome is null and
      ((latitude is null and longitude is null) or (latitude is not null and longitude is not null))) or
    (location_mode = 'CAMPUS' and campus_nome is not null and latitude is null and longitude is null)
);

alter table contexto_integracao_externa add column auth_mode varchar(30) not null default 'NONE';
alter table contexto_integracao_externa add column credential_query_param varchar(120);
alter table contexto_integracao_externa add column coordinate_query_param varchar(120);
alter table contexto_integracao_externa add column query_params_json varchar(4000);
alter table contexto_integracao_externa add column observed_at_format varchar(30) not null default 'ISO_INSTANT';
alter table contexto_integracao_externa add column last_outcome varchar(30);
alter table contexto_integracao_externa add column last_observation_at timestamptz;
alter table contexto_integracao_externa add constraint ck_contexto_auth_mode check (auth_mode in ('NONE','HEADER','QUERY'));
alter table contexto_integracao_externa add constraint ck_contexto_observed_format check (observed_at_format in ('ISO_INSTANT','UNIX_SECONDS'));

create table contexto_coleta_execucao (
    id uuid primary key,
    integracao_id uuid not null references contexto_integracao_externa(id) on delete cascade,
    started_at timestamptz not null,
    finished_at timestamptz not null,
    outcome varchar(30) not null,
    observation_at timestamptz,
    medicao_id uuid references medicao(id),
    latitude numeric(9,6),
    longitude numeric(9,6),
    location_source varchar(20) not null,
    campus_nome varchar(255),
    location_hash varchar(64) not null,
    error_message varchar(1000),
    constraint ck_coleta_outcome check (outcome in ('NEW_MEASUREMENT','REPEATED_OBSERVATION','TEST_SUCCESS','FAILED'))
);
create index idx_coleta_integracao_started on contexto_coleta_execucao(integracao_id, started_at desc);
create index idx_coleta_medicao on contexto_coleta_execucao(medicao_id);

comment on table contexto_coleta_execucao is 'Auditoria da consulta HTTP, incluindo localizacao efetivamente utilizada sem armazenar credenciais.';
