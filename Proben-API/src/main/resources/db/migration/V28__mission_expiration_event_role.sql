-- Allows explicit expiration events while keeping existing roles unchanged.
alter table evento_definicao
    drop constraint if exists ck_evento_definicao_papel;

alter table evento_definicao
    add constraint ck_evento_definicao_papel
    check (papel in ('ATRIBUICAO', 'PROGRESSO', 'CONCLUSAO', 'EXPIRACAO'));