package com.procel.api.service.analytics;

import com.procel.api.config.AnalyticsBucketQueryProperties;
import com.procel.api.dto.analytics.NumericBucketDTOs;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.repository.rooms.CompartimentoRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.repository.sensors.SensorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NumericBucketQueryService {
    private final JdbcTemplate jdbcTemplate;
    private final SensorRepository sensorRepository;
    private final ParametroDefRepository parametroDefRepository;
    private final CompartimentoRepository compartimentoRepository;
    private final AnalyticsBucketQueryProperties properties;
    private final ApiObservabilityMetrics metrics;

    public NumericBucketQueryService(
            JdbcTemplate jdbcTemplate,
            SensorRepository sensorRepository,
            ParametroDefRepository parametroDefRepository,
            CompartimentoRepository compartimentoRepository,
            AnalyticsBucketQueryProperties properties,
            ApiObservabilityMetrics metrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sensorRepository = sensorRepository;
        this.parametroDefRepository = parametroDefRepository;
        this.compartimentoRepository = compartimentoRepository;
        this.properties = properties;
        this.metrics = metrics;
    }

    public NumericBucketDTOs.NumericBucketPage list(NumericBucketQuery query) {
        Instant startedAt = Instant.now();
        try {
            validate(query);
            ensureReferences(query);
            int page = page(query);
            int size = size(query);
            SqlFilter filter = filter(query);
            long total = jdbcTemplate.queryForObject("select count(*) " + filter.fromWhere(), Long.class, filter.args().toArray());
            int totalPages = total == 0 ? 0 : Math.toIntExact((total + size - 1) / size);

            List<Object> args = new ArrayList<>(filter.args());
            args.add(size);
            args.add((long) page * size);
            List<NumericBucketDTOs.NumericBucketResponse> content = jdbcTemplate.query("""
                    select bucket.sensor_external_id, sensor.nome as sensor_nome,
                           bucket.parametro_def_id, parametro.nome as parametro_nome,
                           parametro.numeric_unit as unidade, bucket.compartimento_id,
                           bucket.bucket_start, bucket.bucket_end, bucket.average_value,
                           bucket.minimum_value, bucket.maximum_value, bucket.sample_count,
                           bucket.aggregation_version
                    """ + filter.fromWhere() + """
                    order by bucket.bucket_start asc, bucket.sensor_external_id asc,
                             bucket.parametro_def_id asc, bucket.bucket_end asc,
                             bucket.aggregation_version asc
                    limit ? offset ?
                    """, bucketMapper(), args.toArray());

            metrics.analyticsQuery("list", "success", Duration.between(startedAt, Instant.now()));
            return new NumericBucketDTOs.NumericBucketPage(content, page, size, total, totalPages);
        } catch (RuntimeException ex) {
            metrics.analyticsQuery("list", "error", Duration.between(startedAt, Instant.now()));
            throw ex;
        }
    }

    public List<NumericBucketDTOs.NumericBucketSummaryResponse> summary(NumericBucketQuery query) {
        Instant startedAt = Instant.now();
        try {
            validate(query);
            ensureReferences(query);
            SqlFilter filter = filter(query);
            List<NumericBucketDTOs.NumericBucketSummaryResponse> result = jdbcTemplate.query("""
                    select bucket.sensor_external_id, sensor.nome as sensor_nome,
                           bucket.parametro_def_id, parametro.nome as parametro_nome,
                           parametro.numeric_unit as unidade, bucket.compartimento_id,
                           min(bucket.minimum_value) as minimum_value,
                           max(bucket.maximum_value) as maximum_value,
                           sum(bucket.sample_count) as sample_count,
                           sum(bucket.average_value * bucket.sample_count) / nullif(sum(bucket.sample_count), 0) as average_value,
                           bucket.aggregation_version,
                           count(*) as bucket_count
                    """ + filter.fromWhere() + """
                    group by bucket.sensor_external_id, sensor.nome, bucket.parametro_def_id,
                             parametro.nome, parametro.numeric_unit, bucket.compartimento_id, bucket.aggregation_version
                    order by bucket.sensor_external_id asc, bucket.parametro_def_id asc,
                             bucket.compartimento_id asc, bucket.aggregation_version asc
                    """, (rs, rowNum) -> new NumericBucketDTOs.NumericBucketSummaryResponse(
                    rs.getString("sensor_external_id"),
                    rs.getString("sensor_nome"),
                    rs.getObject("parametro_def_id", UUID.class),
                    rs.getString("parametro_nome"),
                    rs.getString("unidade"),
                    rs.getString("compartimento_id"),
                    query.from(),
                    query.to(),
                    rs.getBigDecimal("average_value"),
                    rs.getBigDecimal("minimum_value"),
                    rs.getBigDecimal("maximum_value"),
                    rs.getLong("sample_count"),
                    rs.getInt("aggregation_version"),
                    rs.getLong("bucket_count")
            ), filter.args().toArray());
            metrics.analyticsQuery("summary", "success", Duration.between(startedAt, Instant.now()));
            return result;
        } catch (RuntimeException ex) {
            metrics.analyticsQuery("summary", "error", Duration.between(startedAt, Instant.now()));
            throw ex;
        }
    }

    private void validate(NumericBucketQuery query) {
        if (query.from() == null) {
            throw new IllegalArgumentException("from is required");
        }
        if (query.to() == null) {
            throw new IllegalArgumentException("to is required");
        }
        if (!query.from().isBefore(query.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (Duration.between(query.from(), query.to()).compareTo(properties.getMaxPeriod()) > 0) {
            throw new IllegalArgumentException("period is above the maximum allowed");
        }
        if (query.page() != null && query.page() < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (query.size() != null && (query.size() < 1 || query.size() > properties.getMaxPageSize())) {
            throw new IllegalArgumentException("size must be between 1 and " + properties.getMaxPageSize());
        }
        if (query.aggregationVersion() != null && query.aggregationVersion() <= 0) {
            throw new IllegalArgumentException("aggregationVersion must be positive");
        }
    }

    private int page(NumericBucketQuery query) {
        return query.page() == null ? 0 : query.page();
    }

    private int size(NumericBucketQuery query) {
        if (query.size() != null) {
            return query.size();
        }
        return Math.min(50, properties.getMaxPageSize());
    }

    private void ensureReferences(NumericBucketQuery query) {
        if (query.sensorExternalId() != null && !sensorRepository.existsById(query.sensorExternalId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "sensorExternalId not found");
        }
        if (query.parametroDefId() != null && !parametroDefRepository.existsById(query.parametroDefId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "parametroDefId not found");
        }
        if (query.compartimentoId() != null && !compartimentoRepository.existsById(query.compartimentoId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "compartimentoId not found");
        }
    }

    private SqlFilter filter(NumericBucketQuery query) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                from analytics_numeric_bucket bucket
                join sensor sensor on sensor.external_id = bucket.sensor_external_id
                join parametro_def parametro on parametro.id = bucket.parametro_def_id
                where bucket.bucket_start >= ?
                  and bucket.bucket_end <= ?
                """);
        args.add(Timestamp.from(query.from()));
        args.add(Timestamp.from(query.to()));
        if (query.sensorExternalId() != null) {
            sql.append("  and bucket.sensor_external_id = ?\n");
            args.add(query.sensorExternalId());
        }
        if (query.parametroDefId() != null) {
            sql.append("  and bucket.parametro_def_id = ?\n");
            args.add(query.parametroDefId());
        }
        if (query.compartimentoId() != null) {
            sql.append("  and bucket.compartimento_id = ?\n");
            args.add(query.compartimentoId());
        }
        if (query.aggregationVersion() != null) {
            sql.append("  and bucket.aggregation_version = ?\n");
            args.add(query.aggregationVersion());
        }
        return new SqlFilter(sql.toString(), args);
    }

    private RowMapper<NumericBucketDTOs.NumericBucketResponse> bucketMapper() {
        return (rs, rowNum) -> new NumericBucketDTOs.NumericBucketResponse(
                rs.getString("sensor_external_id"),
                rs.getString("sensor_nome"),
                rs.getObject("parametro_def_id", UUID.class),
                rs.getString("parametro_nome"),
                rs.getString("unidade"),
                rs.getString("compartimento_id"),
                rs.getTimestamp("bucket_start").toInstant(),
                rs.getTimestamp("bucket_end").toInstant(),
                rs.getBigDecimal("average_value"),
                rs.getBigDecimal("minimum_value"),
                rs.getBigDecimal("maximum_value"),
                rs.getLong("sample_count"),
                rs.getInt("aggregation_version")
        );
    }

    private record SqlFilter(String fromWhere, List<Object> args) {
    }
}
