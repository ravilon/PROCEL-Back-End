create table campus (
    nome varchar(255) not null,
    constraint ux_campus_nome primary key (nome)
);

create table compartimento (
    area numeric(10,2),
    capacidade integer,
    pavimento integer,
    lotacao_raw varchar(40),
    id varchar(80) not null,
    tipo varchar(80) not null,
    predio_id varchar(600) not null,
    nome varchar(255) not null,
    unidade_id varchar(255) not null,
    primary key (id)
);

create table medicao (
    recebido_em timestamp(6) with time zone,
    timestamp timestamp(6) with time zone not null,
    id uuid not null,
    source varchar(60),
    sensor_external_id varchar(120) not null,
    primary key (id)
);

create table parametro_def (
    id uuid not null,
    data_type varchar(20) not null check ((data_type in ('NUMERIC','BOOLEAN','TEXT'))),
    numeric_unit varchar(40),
    tipo_nome varchar(80) not null,
    nome varchar(120) not null,
    descricao varchar(255),
    primary key (id),
    constraint ux_parametro_def_tipo_nome unique (tipo_nome, nome)
);

create table parametro_valor (
    boolean_value boolean,
    numeric_value numeric(18,6),
    id uuid not null,
    medicao_id uuid not null,
    parametro_def_id uuid not null,
    text_value varchar(1000),
    primary key (id),
    constraint ux_valor_medicao_param unique (medicao_id, parametro_def_id)
);

create table pessoa (
    created_at timestamp(6) with time zone not null,
    telefone varchar(40),
    id varchar(80) not null,
    matricula varchar(80),
    email varchar(200) not null,
    nome varchar(200) not null,
    password varchar(255) not null,
    primary key (id),
    constraint uk_pessoa_email unique (email),
    constraint uk_pessoa_matricula unique (matricula)
);

create table pessoa_role (
    role varchar(40) not null check ((role in ('ADMIN','OPERADOR','ANALISTA','USUARIO','INGESTOR'))),
    pessoa_id varchar(80) not null,
    primary key (role, pessoa_id)
);

create table predio (
    id varchar(600) not null,
    campus_id varchar(255) not null,
    nome varchar(255) not null,
    primary key (id),
    constraint ux_predio_nome_campus unique (campus_id, nome)
);

create table presenca (
    checkin_at timestamp(6) with time zone not null,
    checkout_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    id uuid not null,
    source varchar(60),
    compartimento_id varchar(80) not null,
    pessoa_id varchar(80) not null,
    primary key (id)
);

create table sensor (
    compartimento_id varchar(80) not null,
    tipo_nome varchar(80) not null,
    external_id varchar(120) not null,
    nome varchar(255) not null,
    primary key (external_id)
);

create table tipo_de_sensor (
    nome varchar(80) not null,
    primary key (nome)
);

create table unidade (
    nome varchar(255) not null,
    constraint ux_unidade_nome primary key (nome)
);

create index idx_compartimento_predio_id
   on compartimento (predio_id);

create index idx_compartimento_unidade_id
   on compartimento (unidade_id);

create index idx_medicao_sensor_ts
   on medicao (sensor_external_id, timestamp);

create index idx_parametro_def_tipo_nome
   on parametro_def (tipo_nome);

create index idx_valor_medicao_id
   on parametro_valor (medicao_id);

create index idx_valor_param_def_id
   on parametro_valor (parametro_def_id);

create index ix_pessoa_email
   on pessoa (email);

create index ix_pessoa_matricula
   on pessoa (matricula);

create index idx_predio_campus_id
   on predio (campus_id);

create index ix_presenca_pessoa_checkout
   on presenca (pessoa_id, checkout_at);

create index ix_presenca_compartimento_checkout
   on presenca (compartimento_id, checkout_at);

create index ix_presenca_checkin
   on presenca (checkin_at);

create index idx_sensor_compartimento_id
   on sensor (compartimento_id);

create index idx_sensor_tipo_nome
   on sensor (tipo_nome);

alter table if exists compartimento
   add constraint FK3ynmxgphvp4d74wiq3qox9f9f
   foreign key (predio_id)
   references predio;

alter table if exists compartimento
   add constraint FK9cct2yrceuc107jitumgfrr5v
   foreign key (unidade_id)
   references unidade;

alter table if exists medicao
   add constraint FKebxigrfimjllyfeucuecq6t0g
   foreign key (sensor_external_id)
   references sensor;

alter table if exists parametro_def
   add constraint FKnqqirvaar485kib9v4tmb0mpl
   foreign key (tipo_nome)
   references tipo_de_sensor;

alter table if exists parametro_valor
   add constraint FKmdhyniba3wp8bspxtxfs2o2tt
   foreign key (medicao_id)
   references medicao;

alter table if exists parametro_valor
   add constraint FKgdv56t6gj6ucpw10c72rw3agt
   foreign key (parametro_def_id)
   references parametro_def;

alter table if exists pessoa_role
   add constraint FK564cscnwy2u9k96997q3x3ugy
   foreign key (pessoa_id)
   references pessoa;

alter table if exists predio
   add constraint FKpqufcl6ahd7m596k2t193hgje
   foreign key (campus_id)
   references campus;

alter table if exists presenca
   add constraint fk_presenca_compartimento
   foreign key (compartimento_id)
   references compartimento;

alter table if exists presenca
   add constraint fk_presenca_pessoa
   foreign key (pessoa_id)
   references pessoa;

alter table if exists sensor
   add constraint FK31p3sfcsbn4dog7iyy6ewjfe2
   foreign key (compartimento_id)
   references compartimento;

alter table if exists sensor
   add constraint FK5bxbugmwlobsjk0hfd8nibffn
   foreign key (tipo_nome)
   references tipo_de_sensor;
