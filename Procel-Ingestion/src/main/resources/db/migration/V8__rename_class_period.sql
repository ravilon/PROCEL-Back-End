alter table ocorrencia_aula
    rename column periodo to periodo_aula;

alter index if exists ix_ocorrencia_aula_data_turno_periodo
    rename to ix_ocorrencia_aula_data_turno_periodo_aula;

