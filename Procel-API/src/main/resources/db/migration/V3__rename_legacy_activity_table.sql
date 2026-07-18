do $$
begin
    if to_regclass('public.atividade') is null and to_regclass('public.pessoa_missao') is not null then
        alter table pessoa_missao rename to atividade;
    end if;
end $$;

alter table if exists atividade
    drop constraint if exists pessoa_missao_status_check;

alter table if exists atividade
    drop constraint if exists atividade_status_check;

alter table if exists atividade
    add constraint atividade_status_check
    check ((status in ('PENDENTE','EM_ANDAMENTO','CONCLUIDA','EXPIRADA','CANCELADA')));

alter table if exists atividade
    drop constraint if exists uk_pessoa_missao;

alter table if exists atividade
    drop constraint if exists uk_atividade_pessoa_missao;

alter table if exists atividade
    drop constraint if exists uk_atividade_pessoa_modelo;

alter table if exists atividade
    add constraint uk_atividade_pessoa_modelo
    unique (pessoa_id, missao_id);

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

drop index if exists ix_pessoa_missao_pessoa_status;
drop index if exists ix_pessoa_missao_missao;
drop index if exists ix_pessoa_missao_assigned_at;

create index if not exists ix_atividade_pessoa_status
    on atividade (pessoa_id, status);

create index if not exists ix_atividade_missao
    on atividade (missao_id);

create index if not exists ix_atividade_assigned_at
    on atividade (assigned_at);
