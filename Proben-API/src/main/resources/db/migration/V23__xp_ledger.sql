create table xp_lancamento (
    id uuid primary key default gen_random_uuid(),
    pessoa_id varchar(80) not null,
    atividade_id uuid not null,
    missao_id uuid not null,
    evento_ocorrencia_id uuid,
    tipo varchar(30) not null,
    quantidade integer not null,
    chave_idempotencia varchar(200) not null,
    descricao varchar(1000),
    created_at timestamptz not null default now(),
    created_by varchar(120),
    constraint fk_xp_lancamento_pessoa foreign key (pessoa_id) references pessoa (id),
    constraint fk_xp_lancamento_atividade foreign key (atividade_id) references atividade (id),
    constraint fk_xp_lancamento_missao foreign key (missao_id) references missao (id),
    constraint fk_xp_lancamento_evento_ocorrencia foreign key (evento_ocorrencia_id) references evento_ocorrencia (id),
    constraint uk_xp_lancamento_chave_idempotencia unique (chave_idempotencia),
    constraint ck_xp_lancamento_tipo check (tipo in ('CONCESSAO','ESTORNO','AJUSTE')),
    constraint ck_xp_lancamento_quantidade_nonzero check (quantidade <> 0),
    constraint ck_xp_lancamento_tipo_quantidade check (
        (tipo = 'CONCESSAO' and quantidade > 0)
        or (tipo = 'ESTORNO' and quantidade < 0)
        or (tipo = 'AJUSTE' and quantidade <> 0)
    )
);

create unique index ux_xp_lancamento_auto_completion_atividade
    on xp_lancamento (atividade_id)
    where tipo = 'CONCESSAO' and created_by = 'SYSTEM_AUTO_COMPLETION';

create index ix_xp_lancamento_pessoa_created_at
    on xp_lancamento (pessoa_id, created_at desc, id desc);

create index ix_xp_lancamento_atividade
    on xp_lancamento (atividade_id);

create index ix_xp_lancamento_evento_ocorrencia
    on xp_lancamento (evento_ocorrencia_id);
