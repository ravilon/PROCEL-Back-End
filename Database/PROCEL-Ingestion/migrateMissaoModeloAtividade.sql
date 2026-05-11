-- Migracao para corrigir missoes como modelo/catalogo e atividades como relacao pessoa-missao.
-- Use em bancos que receberam a versao antiga onde missao tinha pessoa_id/status/started_at/completed_at.

create extension if not exists pgcrypto;

create table if not exists pessoa_missao (
    assigned_at timestamp(6) with time zone not null default now(),
    completed_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone,
    id uuid not null default gen_random_uuid(),
    missao_id uuid not null,
    status varchar(30) not null check ((status in ('PENDENTE','EM_ANDAMENTO','CONCLUIDA','CANCELADA'))),
    pessoa_id varchar(80) not null,
    primary key (id),
    constraint uk_pessoa_missao unique (pessoa_id, missao_id)
);

insert into pessoa_missao (pessoa_id, missao_id, status, assigned_at, started_at, completed_at)
select pessoa_id, id, status, created_at, started_at, completed_at
from missao
where pessoa_id is not null
on conflict (pessoa_id, missao_id) do nothing;

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

create index if not exists ix_pessoa_missao_pessoa_status
   on pessoa_missao (pessoa_id, status);

create index if not exists ix_pessoa_missao_missao
   on pessoa_missao (missao_id);

create index if not exists ix_pessoa_missao_assigned_at
   on pessoa_missao (assigned_at);

alter table if exists pessoa_missao
   drop constraint if exists fk_pessoa_missao_missao;

alter table if exists pessoa_missao
   drop constraint if exists fk_pessoa_missao_pessoa;

alter table if exists pessoa_missao
   add constraint fk_pessoa_missao_missao
   foreign key (missao_id)
   references missao;

alter table if exists pessoa_missao
   add constraint fk_pessoa_missao_pessoa
   foreign key (pessoa_id)
   references pessoa;
