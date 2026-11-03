create table sensor_integration_profile (
    id uuid not null default gen_random_uuid(),
    nome varchar(120) not null,
    descricao varchar(500),
    source varchar(30) not null,
    ativo boolean not null default true,
    created_at timestamp(6) with time zone not null default now(),
    updated_at timestamp(6) with time zone not null default now(),
    primary key (id),
    constraint ux_sensor_integration_profile_nome unique (nome),
    constraint ck_sensor_integration_profile_source
        check (source in ('MQTT','REST','FILE','API'))
);

create table sensor_integration_parser_version (
    id uuid not null default gen_random_uuid(),
    profile_id uuid not null,
    version integer not null,
    status varchar(20) not null,
    sensor_resolution_mode varchar(30) not null,
    message_id_pointer varchar(500) not null,
    sensor_external_id_pointer varchar(500),
    timestamp_pointer varchar(500) not null,
    source_received_at_pointer varchar(500),
    timestamp_format varchar(80) not null default 'ISO_INSTANT',
    created_at timestamp(6) with time zone not null default now(),
    updated_at timestamp(6) with time zone not null default now(),
    published_at timestamp(6) with time zone,
    primary key (id),
    constraint fk_parser_version_profile
        foreign key (profile_id)
        references sensor_integration_profile (id)
        on delete restrict,
    constraint ux_parser_version_profile_version unique (profile_id, version),
    constraint ux_parser_version_id_profile unique (id, profile_id),
    constraint ck_parser_version_status
        check (status in ('DRAFT','ACTIVE','INACTIVE')),
    constraint ck_parser_version_publication
        check (
            (status = 'DRAFT' and published_at is null)
            or
            (status in ('ACTIVE','INACTIVE') and published_at is not null)
        ),
    constraint ck_parser_version_resolution_mode
        check (sensor_resolution_mode in ('ROUTE_SENSOR','PAYLOAD_POINTER')),
    constraint ck_parser_version_sensor_pointer
        check (
            (sensor_resolution_mode = 'ROUTE_SENSOR' and sensor_external_id_pointer is null)
            or
            (sensor_resolution_mode = 'PAYLOAD_POINTER' and sensor_external_id_pointer is not null)
        ),
    constraint ck_parser_version_timestamp_format
        check (timestamp_format = 'ISO_INSTANT')
);

create table sensor_integration_value_mapping (
    id uuid not null default gen_random_uuid(),
    parser_version_id uuid not null,
    parameter_name varchar(120) not null,
    value_pointer varchar(500) not null,
    required boolean not null default true,
    created_at timestamp(6) with time zone not null default now(),
    primary key (id),
    constraint fk_value_mapping_parser_version
        foreign key (parser_version_id)
        references sensor_integration_parser_version (id)
        on delete restrict,
    constraint ux_value_mapping_version_parameter
        unique (parser_version_id, parameter_name)
);

create table sensor_integration_binding (
    id uuid not null default gen_random_uuid(),
    sensor_external_id varchar(120) not null,
    profile_id uuid not null,
    ativo boolean not null default true,
    created_at timestamp(6) with time zone not null default now(),
    deactivated_at timestamp(6) with time zone,
    primary key (id),
    constraint fk_binding_sensor
        foreign key (sensor_external_id)
        references sensor (external_id)
        on delete restrict,
    constraint fk_binding_profile
        foreign key (profile_id)
        references sensor_integration_profile (id)
        on delete restrict,
    constraint ck_integration_binding_activation
        check (
            (ativo = true and deactivated_at is null)
            or
            (ativo = false and deactivated_at is not null)
        )
);

create unique index ux_parser_version_active_profile
    on sensor_integration_parser_version (profile_id)
    where status = 'ACTIVE';

create unique index ux_binding_active_sensor_profile
    on sensor_integration_binding (sensor_external_id, profile_id)
    where ativo = true;

create index idx_parser_version_profile_status
    on sensor_integration_parser_version (profile_id, status);

create index idx_value_mapping_parser_version
    on sensor_integration_value_mapping (parser_version_id);

create index idx_binding_profile_active
    on sensor_integration_binding (profile_id, ativo);

create index idx_binding_sensor_active
    on sensor_integration_binding (sensor_external_id, ativo);
