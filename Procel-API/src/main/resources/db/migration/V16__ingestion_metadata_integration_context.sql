do $$
begin
    if exists (
        select 1
        from medicao_ingestao_metadata
        group by producer_id, sensor_external_id, message_id
        having count(*) > 1
    ) then
        raise exception
            'Cannot migrate idempotency indexes: duplicate direct ingestion keys found in medicao_ingestao_metadata';
    end if;
end $$;

alter table medicao_ingestao_metadata
    add column integration_profile_id uuid,
    add column parser_version_id uuid;

alter table medicao_ingestao_metadata
    add constraint ck_medicao_ingestao_integration_context
    check (
        (integration_profile_id is null and parser_version_id is null)
        or
        (integration_profile_id is not null and parser_version_id is not null)
    );

alter table medicao_ingestao_metadata
    add constraint fk_metadata_integration_profile
    foreign key (integration_profile_id)
    references sensor_integration_profile (id)
    on delete restrict;

alter table medicao_ingestao_metadata
    add constraint fk_metadata_parser_version_profile
    foreign key (parser_version_id, integration_profile_id)
    references sensor_integration_parser_version (id, profile_id)
    on delete restrict;

do $$
begin
    if exists (
        select 1
        from medicao_ingestao_metadata
        where integration_profile_id is not null
        group by integration_profile_id, sensor_external_id, message_id
        having count(*) > 1
    ) then
        raise exception
            'Cannot migrate idempotency indexes: duplicate profile ingestion keys found in medicao_ingestao_metadata';
    end if;
end $$;

alter table medicao_ingestao_metadata
    drop constraint ux_medicao_ingestao_producer_sensor_message;

create unique index ux_metadata_direct_idempotency
    on medicao_ingestao_metadata (producer_id, sensor_external_id, message_id)
    where integration_profile_id is null;

create unique index ux_metadata_profile_idempotency
    on medicao_ingestao_metadata (integration_profile_id, sensor_external_id, message_id)
    where integration_profile_id is not null;

create index idx_metadata_integration_profile
    on medicao_ingestao_metadata (integration_profile_id);

create index idx_metadata_parser_version
    on medicao_ingestao_metadata (parser_version_id);
