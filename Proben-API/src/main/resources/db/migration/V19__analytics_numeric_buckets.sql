create table analytics_numeric_bucket (
    id uuid not null default gen_random_uuid(),
    sensor_external_id varchar(120) not null,
    parametro_def_id uuid not null,
    compartimento_id varchar(80) not null,
    bucket_start timestamp(6) with time zone not null,
    bucket_end timestamp(6) with time zone not null,
    aggregation_version integer not null,
    average_value numeric(18,6) not null,
    minimum_value numeric(18,6) not null,
    maximum_value numeric(18,6) not null,
    sample_count bigint not null,
    source_job_id uuid,
    source_window_id uuid,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    primary key (id),
    constraint ux_analytics_numeric_bucket_identity unique (
        sensor_external_id,
        parametro_def_id,
        bucket_start,
        bucket_end,
        aggregation_version
    ),
    constraint fk_analytics_numeric_bucket_sensor foreign key (sensor_external_id) references sensor (external_id),
    constraint fk_analytics_numeric_bucket_parametro foreign key (parametro_def_id) references parametro_def (id),
    constraint fk_analytics_numeric_bucket_compartimento foreign key (compartimento_id) references compartimento (id),
    constraint fk_analytics_numeric_bucket_job foreign key (source_job_id) references analytics_aggregation_job (id) on delete set null,
    constraint fk_analytics_numeric_bucket_window foreign key (source_window_id) references analytics_aggregation_window (id) on delete set null,
    constraint ck_analytics_numeric_bucket_period check (bucket_start < bucket_end),
    constraint ck_analytics_numeric_bucket_version check (aggregation_version > 0),
    constraint ck_analytics_numeric_bucket_count check (sample_count > 0)
);

create index ix_analytics_numeric_bucket_sensor_start
    on analytics_numeric_bucket (sensor_external_id, bucket_start);

create index ix_analytics_numeric_bucket_param_start
    on analytics_numeric_bucket (parametro_def_id, bucket_start);

create index ix_analytics_numeric_bucket_compartimento_start
    on analytics_numeric_bucket (compartimento_id, bucket_start);

create index ix_parametro_valor_numeric_aggregation
    on parametro_valor (medicao_id, parametro_def_id)
    where numeric_value is not null;
