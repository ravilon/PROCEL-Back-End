create table regra_ambiente (
    id uuid primary key default gen_random_uuid(),
    nome varchar(200) not null,
    descricao varchar(2000),
    categoria varchar(80) not null,
    ativo boolean not null default true,
    padrao boolean not null default false,
    escopo varchar(20) not null,
    campus_nome varchar(255),
    compartimento_id varchar(80),
    timezone varchar(80) not null default 'America/Sao_Paulo',
    cron_expression varchar(120) not null default '0 */10 * * * *',
    dias_semana smallint[] not null default '{1,2,3,4,5,6,7}',
    vigencia_inicio timestamptz,
    vigencia_fim timestamptz,
    politica_ocupacao varchar(40) not null,
    politica_beneficiario varchar(40) not null,
    monitorar_sem_pontos boolean not null default false,
    pontuacao jsonb not null default '{}'::jsonb,
    configuracao jsonb not null default '{}'::jsonb,
    versao integer not null default 1,
    created_at timestamptz not null default now(),
    created_by varchar(120) not null,
    updated_at timestamptz not null default now(),
    updated_by varchar(120) not null,
    constraint fk_regra_ambiente_campus foreign key (campus_nome) references campus(nome),
    constraint fk_regra_ambiente_compartimento foreign key (compartimento_id) references compartimento(id),
    constraint ck_regra_ambiente_categoria check (categoria ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    constraint ck_regra_ambiente_escopo check (escopo in ('GLOBAL','CAMPUS','ROOM')),
    constraint ck_regra_ambiente_ocupacao check (politica_ocupacao in ('IGNORE','REQUIRE_OCCUPIED','REQUIRE_SCHEDULED_USE')),
    constraint ck_regra_ambiente_beneficiario check (politica_beneficiario in ('NONE','SCHEDULED_STUDENTS','CONFIRMED_PRESENCE')),
    constraint ck_regra_ambiente_vigencia check (vigencia_fim is null or vigencia_inicio is null or vigencia_fim > vigencia_inicio),
    constraint ck_regra_ambiente_scope_fields check (
        (escopo='ROOM' and not padrao and compartimento_id is not null and campus_nome is null)
        or (escopo='CAMPUS' and padrao and campus_nome is not null and compartimento_id is null)
        or (escopo='GLOBAL' and padrao and campus_nome is null and compartimento_id is null)
    )
);

create unique index ux_regra_ambiente_room_active
    on regra_ambiente(compartimento_id,categoria)
    where ativo and escopo='ROOM';
create unique index ux_regra_ambiente_campus_default_active
    on regra_ambiente(categoria,campus_nome)
    where ativo and padrao and escopo='CAMPUS';
create unique index ux_regra_ambiente_global_default_active
    on regra_ambiente(categoria)
    where ativo and padrao and escopo='GLOBAL';
create index ix_regra_ambiente_resolution
    on regra_ambiente(ativo,categoria,escopo,compartimento_id,campus_nome);

create table regra_ambiente_execucao (
    id uuid primary key default gen_random_uuid(),
    regra_id uuid not null,
    regra_versao integer not null,
    regra_snapshot jsonb not null,
    compartimento_id varchar(80) not null,
    categoria varchar(80) not null,
    origem varchar(30) not null,
    tipo_execucao varchar(20) not null,
    instante_previsto timestamptz not null,
    instante_real timestamptz,
    timezone varchar(80) not null,
    status varchar(40) not null default 'PENDING',
    resultado varchar(40),
    motivo varchar(1000),
    evidencias jsonb not null default '{}'::jsonb,
    contexto_academico jsonb,
    ocupacao jsonb,
    beneficiarios jsonb not null default '[]'::jsonb,
    pontos_calculados integer not null default 0,
    lancamentos jsonb not null default '[]'::jsonb,
    attempts integer not null default 0,
    available_at timestamptz not null default now(),
    claimed_at timestamptz,
    lease_until timestamptz,
    lease_token uuid,
    last_error varchar(1000),
    started_at timestamptz,
    finished_at timestamptz,
    duration_ms bigint,
    idempotency_key varchar(300) not null,
    created_at timestamptz not null default now(),
    created_by varchar(120) not null,
    constraint fk_regra_ambiente_execucao_regra foreign key (regra_id) references regra_ambiente(id),
    constraint fk_regra_ambiente_execucao_compartimento foreign key (compartimento_id) references compartimento(id),
    constraint uk_regra_ambiente_execucao_idempotency unique(idempotency_key),
    constraint ck_regra_ambiente_execucao_origem check (origem in ('SPECIFIC','CAMPUS_DEFAULT','GLOBAL_DEFAULT')),
    constraint ck_regra_ambiente_execucao_tipo check (tipo_execucao in ('SCHEDULED','MANUAL')),
    constraint ck_regra_ambiente_execucao_status check (status in ('PENDING','PROCESSING','RETRY','COMPLETED','FAILED')),
    constraint ck_regra_ambiente_execucao_resultado check (resultado is null or resultado in ('SATISFIED','NOT_SATISFIED','SKIPPED_NO_DATA','SKIPPED_STALE_DATA','SKIPPED_FUTURE_DATA','SKIPPED_INSUFFICIENT_COVERAGE','SKIPPED_AMBIGUOUS_RULE','SKIPPED_NO_OCCUPANCY','SKIPPED_NO_SCHEDULED_USE','SKIPPED_NO_BENEFICIARY','FAILED'))
);

create index ix_regra_ambiente_execucao_claim
    on regra_ambiente_execucao(status,available_at,lease_until,created_at);
create index ix_regra_ambiente_execucao_history
    on regra_ambiente_execucao(compartimento_id,categoria,instante_previsto desc);
create index ix_regra_ambiente_execucao_regra
    on regra_ambiente_execucao(regra_id,created_at desc);

alter table xp_lancamento alter column atividade_id drop not null;
alter table xp_lancamento alter column missao_id drop not null;
alter table xp_lancamento add column origem varchar(40) not null default 'MISSION';
alter table xp_lancamento add column regra_ambiente_execucao_id uuid;
alter table xp_lancamento add column compartimento_id varchar(80);
alter table xp_lancamento add constraint fk_xp_regra_ambiente_execucao
    foreign key (regra_ambiente_execucao_id) references regra_ambiente_execucao(id);
alter table xp_lancamento add constraint fk_xp_regra_ambiente_compartimento
    foreign key (compartimento_id) references compartimento(id);
alter table xp_lancamento add constraint ck_xp_lancamento_origem
    check (origem in ('MISSION','ENVIRONMENT_RULE'));
alter table xp_lancamento add constraint ck_xp_lancamento_origem_links check (
    (origem='MISSION' and atividade_id is not null and missao_id is not null and regra_ambiente_execucao_id is null)
    or (origem='ENVIRONMENT_RULE' and atividade_id is null and missao_id is null and regra_ambiente_execucao_id is not null and compartimento_id is not null)
);
create unique index ux_xp_lancamento_environment_beneficiary
    on xp_lancamento(regra_ambiente_execucao_id,pessoa_id)
    where origem='ENVIRONMENT_RULE' and tipo='CONCESSAO';
create index ix_xp_lancamento_environment_execution
    on xp_lancamento(regra_ambiente_execucao_id);
