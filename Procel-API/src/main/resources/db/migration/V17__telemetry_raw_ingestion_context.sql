alter table medicao_ingestao_metadata
    add column original_producer_id varchar(80),
    add column raw_message_id varchar(160),
    add column raw_telemetry_event_id varchar(120),
    add column raw_received_at timestamp(6) with time zone,
    add column raw_source_timestamp timestamp(6) with time zone;

alter table medicao_ingestao_metadata
    add constraint ck_metadata_telemetry_raw_context
    check (
        (
            original_producer_id is null
            and raw_message_id is null
            and raw_telemetry_event_id is null
            and raw_received_at is null
            and raw_source_timestamp is null
        )
        or
        (
            original_producer_id is not null
            and raw_message_id is not null
            and raw_telemetry_event_id is not null
            and raw_received_at is not null
            and integration_profile_id is not null
            and parser_version_id is not null
        )
    );

drop index ux_metadata_profile_idempotency;

create unique index ux_metadata_profile_idempotency
    on medicao_ingestao_metadata (integration_profile_id, sensor_external_id, message_id)
    where integration_profile_id is not null
      and raw_telemetry_event_id is null;

create unique index ux_metadata_telemetry_raw_idempotency
    on medicao_ingestao_metadata (integration_profile_id, sensor_external_id, original_producer_id, raw_message_id)
    where raw_telemetry_event_id is not null;

create index idx_metadata_raw_telemetry_event_id
    on medicao_ingestao_metadata (raw_telemetry_event_id);
