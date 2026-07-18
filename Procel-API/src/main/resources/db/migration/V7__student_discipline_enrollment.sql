create table aluno_disciplina (
    id uuid not null,
    pessoa_id varchar(80) not null,
    disciplina_id bigint not null,
    turma varchar(80) not null,
    periodo_letivo varchar(20) not null,
    status varchar(20) not null
        check (status in ('ATIVA', 'CONCLUIDA', 'CANCELADA')),
    vinculado_em timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_aluno_disciplina
        unique (pessoa_id, disciplina_id, turma, periodo_letivo)
);

create index ix_aluno_disciplina_pessoa_periodo
    on aluno_disciplina (pessoa_id, periodo_letivo);

create index ix_aluno_disciplina_disciplina
    on aluno_disciplina (disciplina_id);

alter table aluno_disciplina
    add constraint fk_aluno_disciplina_pessoa
    foreign key (pessoa_id)
    references pessoa;

alter table aluno_disciplina
    add constraint fk_aluno_disciplina_disciplina
    foreign key (disciplina_id)
    references disciplina;

