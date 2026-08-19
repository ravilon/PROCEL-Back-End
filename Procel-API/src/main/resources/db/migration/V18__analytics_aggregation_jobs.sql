create table analytics_aggregation_job (
    id uuid not null default gen_random_uuid(),
    idempotency_key varchar(64) not null,
    requested_from timestamp(6) with time zone not null,
    requested_to timestamp(6) with time zone not null,
    window_duration_seconds bigint not null,
    sensor_external_id varchar(120),
    compartimento_id varchar(80),
    requested_by varchar(80) not null,
    created_at timestamp(6) with time zone not null,
    started_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    status varchar(30) not null,
    error text,
    total_windows integer not null,
    completed_windows integer not null default 0,
    failed_windows integer not null default 0,
    processing_windows integer not null default 0,
    primary key (id),
    constraint ux_analytics_aggregation_job_idempotency unique (idempotency_key),
    constraint fk_analytics_aggregation_job_sensor foreign key (sensor_external_id) references sensor (external_id),
    constraint fk_analytics_aggregation_job_compartimento foreign key (compartimento_id) references compartimento (id),
    constraint ck_analytics_aggregation_job_period check (requested_from < requested_to),
    constraint ck_analytics_aggregation_job_window_duration check (window_duration_seconds > 0),
    constraint ck_analytics_aggregation_job_status check (status in ('PENDING','PROCESSING','COMPLETED','FAILED')),
    constraint ck_analytics_aggregation_job_progress check (
        total_windows > 0
        and completed_windows >= 0
        and failed_windows >= 0
        and processing_windows >= 0
        and completed_windows + failed_windows + processing_windows <= total_windows
    )
);

create table analytics_aggregation_window (
    id uuid not null default gen_random_uuid(),
    job_id uuid not null,
    window_index integer not null,
    window_from timestamp(6) with time zone not null,
    window_to timestamp(6) with time zone not null,
    status varchar(30) not null,
    attempts integer not null default 0,
    next_attempt_at timestamp(6) with time zone not null,
    locked_at timestamp(6) with time zone,
    locked_by varchar(120),
    started_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    error text,
    primary key (id),
    constraint fk_analytics_aggregation_window_job foreign key (job_id) references analytics_aggregation_job (id) on delete cascade,
    constraint ux_analytics_aggregation_window_index unique (job_id, window_index),
    constraint ux_analytics_aggregation_window_period unique (job_id, window_from, window_to),
    constraint ck_analytics_aggregation_window_period check (window_from < window_to),
    constraint ck_analytics_aggregation_window_status check (status in ('PENDING','PROCESSING','COMPLETED','FAILED')),
    constraint ck_analytics_aggregation_window_attempts check (attempts >= 0),
    constraint ck_analytics_aggregation_window_index check (window_index >= 0)
);

create index ix_analytics_aggregation_job_status_created
    on analytics_aggregation_job (status, created_at);

create index ix_analytics_aggregation_window_claim
    on analytics_aggregation_window (status, next_attempt_at, locked_at, window_index);

create index ix_analytics_aggregation_window_job_status
    on analytics_aggregation_window (job_id, status);
