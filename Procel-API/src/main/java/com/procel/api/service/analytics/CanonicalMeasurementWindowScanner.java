package com.procel.api.service.analytics;

import com.procel.api.config.AnalyticsAggregationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Service
public class CanonicalMeasurementWindowScanner implements AggregationWindowProcessor {
    private final JdbcTemplate jdbcTemplate;
    private final AnalyticsAggregationProperties properties;

    public CanonicalMeasurementWindowScanner(
            JdbcTemplate jdbcTemplate,
            AnalyticsAggregationProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void process(AggregationWindowWork work) {
        deleteCurrentWindowBuckets(work);
        insertCurrentWindowBuckets(work);
    }

    private void deleteCurrentWindowBuckets(AggregationWindowWork work) {
        jdbcTemplate.update("""
                delete from analytics_numeric_bucket bucket
                using sensor s
                where bucket.sensor_external_id = s.external_id
                  and bucket.bucket_start = ?
                  and bucket.bucket_end = ?
                  and bucket.aggregation_version = ?
                  and (cast(? as varchar) is null or bucket.sensor_external_id = ?)
                  and (cast(? as varchar) is null or s.compartimento_id = ?)
                """,
                Timestamp.from(work.from()),
                Timestamp.from(work.to()),
                properties.getAggregationVersion(),
                work.sensorExternalId(),
                work.sensorExternalId(),
                work.compartimentoId(),
                work.compartimentoId()
        );
    }

    private void insertCurrentWindowBuckets(AggregationWindowWork work) {
        jdbcTemplate.update("""
                insert into analytics_numeric_bucket
                (sensor_external_id, parametro_def_id, compartimento_id, bucket_start, bucket_end,
                 aggregation_version, average_value, minimum_value, maximum_value, sample_count,
                 source_job_id, source_window_id, created_at, updated_at)
                select
                    s.external_id,
                    pv.parametro_def_id,
                    s.compartimento_id,
                    ?,
                    ?,
                    ?,
                    round(avg(pv.numeric_value), 6),
                    round(min(pv.numeric_value), 6),
                    round(max(pv.numeric_value), 6),
                    count(*)::bigint,
                    ?,
                    ?,
                    now(),
                    now()
                from medicao m
                join sensor s on s.external_id = m.sensor_external_id
                join parametro_valor pv on pv.medicao_id = m.id
                join parametro_def pd on pd.id = pv.parametro_def_id
                where m.timestamp >= ?
                  and m.timestamp < ?
                  and pv.numeric_value is not null
                  and pd.data_type = 'NUMERIC'
                  and (cast(? as varchar) is null or s.external_id = ?)
                  and (cast(? as varchar) is null or s.compartimento_id = ?)
                group by s.external_id, pv.parametro_def_id, s.compartimento_id
                on conflict (sensor_external_id, parametro_def_id, bucket_start, bucket_end, aggregation_version)
                do update set
                    compartimento_id = excluded.compartimento_id,
                    average_value = excluded.average_value,
                    minimum_value = excluded.minimum_value,
                    maximum_value = excluded.maximum_value,
                    sample_count = excluded.sample_count,
                    source_job_id = excluded.source_job_id,
                    source_window_id = excluded.source_window_id,
                    updated_at = now()
                """,
                Timestamp.from(work.from()),
                Timestamp.from(work.to()),
                properties.getAggregationVersion(),
                work.jobId(),
                work.windowId(),
                Timestamp.from(work.from()),
                Timestamp.from(work.to()),
                work.sensorExternalId(),
                work.sensorExternalId(),
                work.compartimentoId(),
                work.compartimentoId()
        );
    }
}
