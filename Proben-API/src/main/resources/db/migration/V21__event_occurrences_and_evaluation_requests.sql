create table evento_ocorrencia (
    id uuid not null default gen_random_uuid(),
    evento_definicao_id uuid not null,
    compartimento_id varchar(80) not null,
    periodo_aula_id uuid,
    sensor_external_id varchar(120),
    status varchar(30) not null,
    inicio_em timestamp(6) with time zone not null,
    fim_em timestamp(6) with time zone,
    detectado_em timestamp(6) with time zone not null,
    chave_idempotencia varchar(160) not null,
    contexto_snapshot jsonb not null,
    conteudo_fingerprint varchar(64) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    primary key (id),
    constraint ux_evento_ocorrencia_chave_idempotencia unique (chave_idempotencia),
    constraint fk_evento_ocorrencia_evento foreign key (evento_definicao_id) references evento_definicao (id),
    constraint fk_evento_ocorrencia_compartimento foreign key (compartimento_id) references compartimento (id),
    constraint fk_evento_ocorrencia_periodo_aula foreign key (periodo_aula_id) references periodo_aula (id),
    constraint fk_evento_ocorrencia_sensor foreign key (sensor_external_id) references sensor (external_id),
    constraint ck_evento_ocorrencia_status check (status in ('DETECTADO','CONFIRMADO','INVALIDADO','PROCESSADO')),
    constraint ck_evento_ocorrencia_intervalo check (fim_em is null or inicio_em <= fim_em)
);

create index ix_evento_ocorrencia_status_detectado
    on evento_ocorrencia (status, detectado_em);

create index ix_evento_ocorrencia_evento
    on evento_ocorrencia (evento_definicao_id);

create index ix_evento_ocorrencia_compartimento_inicio
    on evento_ocorrencia (compartimento_id, inicio_em);

create table evento_ocorrencia_evidencia (
    id uuid not null default gen_random_uuid(),
    evento_ocorrencia_id uuid not null,
    medicao_id uuid not null,
    parametro_valor_id uuid,
    papel varchar(30) not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_evento_evidencia_ocorrencia foreign key (evento_ocorrencia_id) references evento_ocorrencia (id),
    constraint fk_evento_evidencia_medicao foreign key (medicao_id) references medicao (id),
    constraint fk_evento_evidencia_parametro_valor foreign key (parametro_valor_id) references parametro_valor (id),
    constraint ck_evento_evidencia_papel check (papel in ('INICIO','CONDICAO','MANUTENCAO','ENCERRAMENTO','BASELINE'))
);

create unique index ux_evento_evidencia_parametro_valor
    on evento_ocorrencia_evidencia (evento_ocorrencia_id, medicao_id, parametro_valor_id, papel)
    where parametro_valor_id is not null;

create unique index ux_evento_evidencia_medicao_sem_parametro
    on evento_ocorrencia_evidencia (evento_ocorrencia_id, medicao_id, papel)
    where parametro_valor_id is null;

create index ix_evento_evidencia_ocorrencia
    on evento_ocorrencia_evidencia (evento_ocorrencia_id);

create index ix_evento_evidencia_medicao
    on evento_ocorrencia_evidencia (medicao_id);

create index ix_evento_evidencia_parametro_valor
    on evento_ocorrencia_evidencia (parametro_valor_id);

create table evento_avaliacao_request (
    id uuid not null default gen_random_uuid(),
    medicao_id uuid not null,
    status varchar(30) not null,
    attempts integer not null default 0,
    available_at timestamp(6) with time zone not null,
    claimed_at timestamp(6) with time zone,
    lease_until timestamp(6) with time zone,
    processed_at timestamp(6) with time zone,
    last_error varchar(1000),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    primary key (id),
    constraint ux_evento_avaliacao_request_medicao unique (medicao_id),
    constraint fk_evento_avaliacao_request_medicao foreign key (medicao_id) references medicao (id),
    constraint ck_evento_avaliacao_request_status check (status in ('PENDING','PROCESSING','RETRY','COMPLETED','FAILED','IGNORED')),
    constraint ck_evento_avaliacao_request_attempts check (attempts >= 0),
    constraint ck_evento_avaliacao_request_terminal_processed check (
        (status in ('COMPLETED','FAILED','IGNORED') and processed_at is not null)
        or (status not in ('COMPLETED','FAILED','IGNORED') and processed_at is null)
    )
);

create index ix_evento_avaliacao_request_status_available
    on evento_avaliacao_request (status, available_at);

create index ix_evento_avaliacao_request_lease
    on evento_avaliacao_request (status, lease_until);
