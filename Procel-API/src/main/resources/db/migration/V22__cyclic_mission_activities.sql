alter table missao
    add column ciclo_tipo varchar(40) not null default 'UNICA',
    add column progresso_necessario integer not null default 1,
    add column conclusao_automatica boolean not null default true;

alter table missao
    add constraint ck_missao_ciclo_tipo
    check (ciclo_tipo in ('UNICA','POR_AULA','POR_PRESENCA','DIARIA','SEMANAL','MENSAL','JANELA_PERSONALIZADA'));

alter table missao
    add constraint ck_missao_progresso_necessario
    check (progresso_necessario >= 1);

alter table atividade
    add column chave_ciclo varchar(160),
    add column ciclo_tipo varchar(40),
    add column ciclo_inicio timestamp(6) with time zone,
    add column ciclo_fim timestamp(6) with time zone,
    add column progresso_atual integer,
    add column progresso_necessario integer,
    add column ultimo_evento_em timestamp(6) with time zone,
    add column conclusao_automatica boolean;

update atividade a
set chave_ciclo = 'UNICA',
    ciclo_tipo = 'UNICA',
    progresso_atual = case when a.status = 'CONCLUIDA' then coalesce(m.progresso_necessario, 1) else 0 end,
    progresso_necessario = coalesce(m.progresso_necessario, 1),
    conclusao_automatica = coalesce(m.conclusao_automatica, true)
from missao m
where a.missao_id = m.id;

update atividade
set chave_ciclo = coalesce(chave_ciclo, 'UNICA'),
    ciclo_tipo = coalesce(ciclo_tipo, 'UNICA'),
    progresso_atual = coalesce(progresso_atual, case when status = 'CONCLUIDA' then 1 else 0 end),
    progresso_necessario = coalesce(progresso_necessario, 1),
    conclusao_automatica = coalesce(conclusao_automatica, true);

alter table atividade
    alter column chave_ciclo set not null,
    alter column ciclo_tipo set not null,
    alter column progresso_atual set not null,
    alter column progresso_necessario set not null,
    alter column conclusao_automatica set not null;

alter table atividade
    drop constraint if exists uk_atividade_pessoa_modelo;

alter table atividade
    add constraint uk_atividade_pessoa_missao_ciclo
    unique (pessoa_id, missao_id, chave_ciclo);

alter table atividade
    add constraint ck_atividade_ciclo_tipo
    check (ciclo_tipo in ('UNICA','POR_AULA','POR_PRESENCA','DIARIA','SEMANAL','MENSAL','JANELA_PERSONALIZADA'));

alter table atividade
    add constraint ck_atividade_progresso
    check (progresso_atual >= 0 and progresso_necessario >= 1);

alter table atividade
    add constraint ck_atividade_ciclo_intervalo
    check (ciclo_inicio is null or ciclo_fim is null or ciclo_inicio <= ciclo_fim);

create index ix_atividade_pessoa_missao_ciclo
    on atividade (pessoa_id, missao_id, chave_ciclo);

create index ix_atividade_missao_ciclo_status
    on atividade (missao_id, ciclo_tipo, status);

create table atividade_evento (
    id uuid not null default gen_random_uuid(),
    atividade_id uuid not null,
    evento_ocorrencia_id uuid not null,
    tipo varchar(30) not null,
    progresso_adicionado integer not null,
    processado_em timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_atividade_evento_atividade foreign key (atividade_id) references atividade (id),
    constraint fk_atividade_evento_ocorrencia foreign key (evento_ocorrencia_id) references evento_ocorrencia (id),
    constraint uk_atividade_evento_ocorrencia_tipo unique (atividade_id, evento_ocorrencia_id, tipo),
    constraint ck_atividade_evento_tipo check (tipo in ('INICIO','PROGRESSO','CONCLUSAO','INVALIDACAO')),
    constraint ck_atividade_evento_progresso check (progresso_adicionado >= 0)
);

create index ix_atividade_evento_atividade
    on atividade_evento (atividade_id);

create index ix_atividade_evento_ocorrencia
    on atividade_evento (evento_ocorrencia_id);
