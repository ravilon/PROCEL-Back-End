create table evento_janela_avaliacao (
    id uuid not null default gen_random_uuid(),
    evento_definicao_id uuid not null,
    compartimento_id varchar(80) not null,
    periodo_aula_id uuid,
    status varchar(30) not null,
    inicio_em timestamp(6) with time zone not null,
    fim_previsto_em timestamp(6) with time zone not null,
    ultima_medicao_em timestamp(6) with time zone,
    proxima_avaliacao_em timestamp(6) with time zone not null,
    lease_until timestamp(6) with time zone,
    attempts integer not null default 0,
    chave_idempotencia varchar(200) not null,
    contexto_snapshot jsonb not null,
    last_error varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    primary key (id),
    constraint ux_evento_janela_chave_idempotencia unique (chave_idempotencia),
    constraint fk_evento_janela_evento foreign key (evento_definicao_id) references evento_definicao (id),
    constraint fk_evento_janela_compartimento foreign key (compartimento_id) references compartimento (id),
    constraint fk_evento_janela_periodo_aula foreign key (periodo_aula_id) references periodo_aula (id),
    constraint ck_evento_janela_status check (status in ('ABERTA','PROCESSING','SATISFEITA','INVALIDADA','EXPIRADA','FAILED')),
    constraint ck_evento_janela_attempts check (attempts >= 0),
    constraint ck_evento_janela_intervalo check (inicio_em < fim_previsto_em),
    constraint ck_evento_janela_terminal_lease check (
        (status in ('SATISFEITA','INVALIDADA','EXPIRADA','FAILED') and lease_until is null)
        or status not in ('SATISFEITA','INVALIDADA','EXPIRADA','FAILED')
    )
);

create index ix_evento_janela_status_proxima
    on evento_janela_avaliacao (status, proxima_avaliacao_em);

create index ix_evento_janela_lease
    on evento_janela_avaliacao (status, lease_until);

create index ix_evento_janela_evento_compartimento
    on evento_janela_avaliacao (evento_definicao_id, compartimento_id, inicio_em);

create index ix_evento_janela_periodo_aula
    on evento_janela_avaliacao (periodo_aula_id)
    where periodo_aula_id is not null;

create table evento_janela_evidencia (
    id uuid not null default gen_random_uuid(),
    evento_janela_avaliacao_id uuid not null,
    medicao_id uuid not null,
    parametro_valor_id uuid,
    papel varchar(30) not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_evento_janela_evidencia_janela foreign key (evento_janela_avaliacao_id) references evento_janela_avaliacao (id),
    constraint fk_evento_janela_evidencia_medicao foreign key (medicao_id) references medicao (id),
    constraint fk_evento_janela_evidencia_parametro_valor foreign key (parametro_valor_id) references parametro_valor (id),
    constraint ck_evento_janela_evidencia_papel check (papel in ('INICIO','CONDICAO','MANUTENCAO','ENCERRAMENTO','BASELINE'))
);

create unique index ux_evento_janela_evidencia_parametro_valor
    on evento_janela_evidencia (evento_janela_avaliacao_id, medicao_id, parametro_valor_id, papel)
    where parametro_valor_id is not null;

create unique index ux_evento_janela_evidencia_medicao_sem_parametro
    on evento_janela_evidencia (evento_janela_avaliacao_id, medicao_id, papel)
    where parametro_valor_id is null;

create index ix_evento_janela_evidencia_janela
    on evento_janela_evidencia (evento_janela_avaliacao_id);

create index ix_evento_janela_evidencia_medicao
    on evento_janela_evidencia (medicao_id);
