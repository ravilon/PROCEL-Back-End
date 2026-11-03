-- Migracao historica para corrigir missoes como modelo/catalogo e atividades como entidade propria.
-- Use em bancos que receberam a versao antiga onde missao tinha pessoa_id/status/started_at/completed_at.
-- A aplicacao agora aplica a evolucao automaticamente via Flyway.

create extension if not exists pgcrypto;

do $$
begin
    if to_regclass('public.atividade') is null and to_regclass('public.pessoa_missao') is not null then
        alter table pessoa_missao rename to atividade;
    end if;
end $$;

create table if not exists atividade (
    assigned_at timestamp(6) with time zone not null default now(),
    completed_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone,
    id uuid not null default gen_random_uuid(),
    missao_id uuid not null,
    status varchar(30) not null,
    pessoa_id varchar(80) not null,
    primary key (id)
);

alter table if exists atividade
    drop constraint if exists pessoa_missao_status_check;

alter table if exists atividade
    drop constraint if exists atividade_status_check;

alter table if exists atividade
    add constraint atividade_status_check
    check ((status in ('PENDENTE','EM_ANDAMENTO','CONCLUIDA','EXPIRADA','CANCELADA')));

insert into atividade (pessoa_id, missao_id, status, assigned_at, started_at, completed_at)
select pessoa_id, id, status, created_at, started_at, completed_at
from missao
where pessoa_id is not null
on conflict do nothing;

alter table missao
    add column if not exists ativo boolean not null default true;

alter table missao
    drop constraint if exists fk_missao_pessoa;

drop index if exists ix_missao_pessoa_status;

alter table missao
    drop column if exists pessoa_id,
    drop column if exists status,
    drop column if exists started_at,
    drop column if exists completed_at;

create index if not exists ix_missao_ativo
   on missao (ativo);

create index if not exists ix_missao_created_at
   on missao (created_at);

alter table if exists atividade
   drop constraint if exists uk_pessoa_missao;

alter table if exists atividade
   drop constraint if exists uk_atividade_pessoa_missao;

alter table if exists atividade
   drop constraint if exists uk_atividade_pessoa_modelo;

alter table if exists atividade
   add constraint uk_atividade_pessoa_modelo
   unique (pessoa_id, missao_id);

drop index if exists ix_pessoa_missao_pessoa_status;
drop index if exists ix_pessoa_missao_missao;
drop index if exists ix_pessoa_missao_assigned_at;

create index if not exists ix_atividade_pessoa_status
   on atividade (pessoa_id, status);

create index if not exists ix_atividade_missao
   on atividade (missao_id);

create index if not exists ix_atividade_assigned_at
   on atividade (assigned_at);

alter table if exists atividade
   drop constraint if exists fk_pessoa_missao_missao;

alter table if exists atividade
   drop constraint if exists fk_pessoa_missao_pessoa;

alter table if exists atividade
   drop constraint if exists fk_atividade_missao;

alter table if exists atividade
   drop constraint if exists fk_atividade_pessoa;

alter table if exists atividade
   add constraint fk_atividade_missao
   foreign key (missao_id)
   references missao;

alter table if exists atividade
   add constraint fk_atividade_pessoa
   foreign key (pessoa_id)
   references pessoa;
