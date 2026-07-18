alter table ocorrencia_aula
    rename to periodo_aula;

alter table periodo_aula
    rename constraint ocorrencia_aula_pkey to periodo_aula_pkey;

alter table periodo_aula
    rename constraint fk_ocorrencia_aula_compartimento
    to fk_periodo_aula_compartimento;

alter table periodo_aula
    rename constraint fk_ocorrencia_aula_disciplina
    to fk_periodo_aula_disciplina;

alter index ix_ocorrencia_aula_compartimento_data
    rename to ix_periodo_aula_compartimento_data;

alter index ix_ocorrencia_aula_disciplina_data
    rename to ix_periodo_aula_disciplina_data;

alter index ix_ocorrencia_aula_data_turno_periodo_aula
    rename to ix_periodo_aula_data_turno_periodo_aula;

