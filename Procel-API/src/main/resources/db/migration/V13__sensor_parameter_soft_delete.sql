alter table if exists parametro_def
    add column if not exists ativo boolean not null default true;

alter table if exists sensor
    add column if not exists ativo boolean not null default true;

create index if not exists idx_parametro_def_tipo_ativo
    on parametro_def (tipo_nome, ativo);

create index if not exists idx_sensor_compartimento_ativo
    on sensor (compartimento_id, ativo);
