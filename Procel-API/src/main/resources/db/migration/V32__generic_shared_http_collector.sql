alter table contexto_integracao_externa
    alter column temperature_json_pointer drop not null,
    add column descricao varchar(1000),
    add column provider_name varchar(255),
    add column attribution varchar(500),
    add column content_type varchar(120) not null default 'application/json',
    add column headers_json text not null default '[]',
    add column mappings_json text,
    add column timestamp_timezone varchar(80),
    add column retry_delay_millis integer not null default 500,
    add column validity_seconds integer not null default 3600,
    add column automatic_collection boolean not null default true;

alter table contexto_integracao_externa drop constraint if exists ck_contexto_auth_mode;
alter table contexto_integracao_externa add constraint ck_contexto_auth_mode
    check (auth_mode in ('NONE','HEADER','QUERY','BEARER'));

alter table contexto_integracao_externa drop constraint if exists ck_contexto_observed_format;
alter table contexto_integracao_externa add constraint ck_contexto_observed_format
    check (observed_at_format in ('ISO_INSTANT','ISO_8601','UNIX_SECONDS','UNIX_MILLISECONDS','COLLECTION_TIME'));

alter table contexto_integracao_externa add constraint ck_contexto_retry_delay
    check (retry_delay_millis between 0 and 60000);
alter table contexto_integracao_externa add constraint ck_contexto_validity
    check (validity_seconds > 0);

update contexto_integracao_externa i
set mappings_json = jsonb_build_array(jsonb_build_object(
        'parametroDefId', p.id,
        'parameterName', p.nome,
        'jsonPointer', i.temperature_json_pointer,
        'expectedType', p.data_type,
        'sourceUnit', coalesce(p.numeric_unit, '°C'),
        'targetUnit', coalesce(p.numeric_unit, '°C'),
        'required', true,
        'conversion', 'NONE'
    ))::text
from sensor s
join parametro_def p on p.tipo_nome=s.tipo_nome and p.nome='temperatura_externa'
where s.external_id=i.sensor_external_id and i.mappings_json is null;

update contexto_integracao_externa
set query_params_json = case
    when coordinate_query_param is not null then
        coalesce(query_params_json::jsonb, '{}'::jsonb)
        || jsonb_build_object(coordinate_query_param, '{{context.coordinates}}')
    else coalesce(query_params_json::jsonb, '{}'::jsonb)
end::text;

comment on column contexto_integracao_externa.headers_json is
    'Tabela JSON de headers configuráveis; valores aceitam apenas templates autorizados.';
comment on column contexto_integracao_externa.mappings_json is
    'Mapeamentos JSON Pointer para ParametroDef do sensor de destino.';
