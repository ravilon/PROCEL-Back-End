create table medicao_ingestao_metadata (
    id uuid not null default gen_random_uuid(),
    medicao_id uuid,
    producer_id varchar(80) not null,
    sensor_external_id varchar(120) not null,
    message_id varchar(160) not null,
    source varchar(30) not null,
    source_received_at timestamp(6) with time zone,
    api_received_at timestamp(6) with time zone not null,
    payload_fingerprint varchar(64) not null,
    status varchar(30) not null,
    created_at timestamp(6) with time zone not null default now(),
    completed_at timestamp(6) with time zone,
    primary key (id),
    constraint ck_medicao_ingestao_source
        check (source in ('MQTT','REST','FILE','API')),
    constraint ck_medicao_ingestao_status
        check (status in ('PROCESSING','COMPLETED')),
    constraint ck_medicao_ingestao_status_consistency
        check (
            (
                status = 'PROCESSING'
                and medicao_id is null
                and completed_at is null
            )
            or
            (
                status = 'COMPLETED'
                and medicao_id is not null
                and completed_at is not null
            )
        ),
    constraint fk_medicao_ingestao_medicao
        foreign key (medicao_id)
        references medicao (id)
        on delete restrict,
    constraint fk_medicao_ingestao_sensor
        foreign key (sensor_external_id)
        references sensor (external_id)
        on delete restrict,
    constraint ux_medicao_ingestao_medicao
        unique (medicao_id),
    constraint ux_medicao_ingestao_producer_sensor_message
        unique (producer_id, sensor_external_id, message_id)
);

create index idx_medicao_ingestao_sensor_message
    on medicao_ingestao_metadata (sensor_external_id, message_id);

create index idx_medicao_ingestao_status_created
    on medicao_ingestao_metadata (status, created_at);
