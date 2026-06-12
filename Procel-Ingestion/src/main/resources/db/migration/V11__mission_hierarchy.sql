alter table missao
    add column parent_id uuid;

alter table missao
    add constraint fk_missao_parent
    foreign key (parent_id)
    references missao(id);

create index ix_missao_parent_id
    on missao(parent_id);
