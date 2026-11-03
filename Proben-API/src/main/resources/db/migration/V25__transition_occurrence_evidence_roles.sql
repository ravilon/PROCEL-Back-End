alter table evento_ocorrencia_evidencia
    drop constraint ck_evento_evidencia_papel;

alter table evento_ocorrencia_evidencia
    add constraint ck_evento_evidencia_papel
        check (papel in ('INICIO','CONDICAO','MANUTENCAO','ENCERRAMENTO','BASELINE','ANTES','DEPOIS'));
