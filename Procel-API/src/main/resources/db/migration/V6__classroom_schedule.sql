create table disciplina (
    id bigint not null,
    nome varchar(255) not null,
    unidade_sigla varchar(80),
    primary key (id)
);

create table ocorrencia_aula (
    data date not null,
    turno integer not null,
    periodo integer not null,
    hora_inicio time not null,
    hora_fim time not null,
    sincronizado_em timestamp(6) with time zone not null,
    disciplina_id bigint,
    id uuid not null,
    compartimento_id varchar(80) not null,
    tipo varchar(20) not null
        check (tipo in ('AULA', 'PROVA', 'RESERVA', 'OUTRO')),
    turma varchar(80),
    source varchar(60),
    descricao varchar(1000) not null,
    primary key (id)
);

create index ix_ocorrencia_aula_compartimento_data
    on ocorrencia_aula (compartimento_id, data);

create index ix_ocorrencia_aula_disciplina_data
    on ocorrencia_aula (disciplina_id, data);

create index ix_ocorrencia_aula_data_turno_periodo
    on ocorrencia_aula (data, turno, periodo);

alter table ocorrencia_aula
    add constraint fk_ocorrencia_aula_compartimento
    foreign key (compartimento_id)
    references compartimento;

alter table ocorrencia_aula
    add constraint fk_ocorrencia_aula_disciplina
    foreign key (disciplina_id)
    references disciplina;
