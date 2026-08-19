package com.procel.api.service.analytics;

import com.procel.api.config.AnalyticsAggregationProperties;
import com.procel.api.dto.analytics.AggregationJobDTOs;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.rooms.CompartimentoRepository;
import com.procel.api.repository.sensors.SensorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AggregationJobService {
    private final JdbcTemplate jdbcTemplate;
    private final SensorRepository sensorRepository;
    private final CompartimentoRepository compartimentoRepository;
    private final AnalyticsAggregationProperties properties;

    public AggregationJobService(
            JdbcTemplate jdbcTemplate,
            SensorRepository sensorRepository,
            CompartimentoRepository compartimentoRepository,
            AnalyticsAggregationProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sensorRepository = sensorRepository;
        this.compartimentoRepository = compartimentoRepository;
        this.properties = properties;
    }

    @Transactional
    public AggregationJobDTOs.AggregationJobResponse create(
            AggregationJobDTOs.CreateAggregationJobRequest request,
            String requestedBy
    ) {
        validate(request);
        String sensorExternalId = normalize(request.sensorExternalId());
        String compartimentoId = normalize(request.compartimentoId());
        ensureReferences(sensorExternalId, compartimentoId);
        String key = idempotencyKey(request.from(), request.to(), request.windowDuration(), sensorExternalId, compartimentoId);
        UUID jobId = insertJob(request, requestedBy, sensorExternalId, compartimentoId, key, windowCount(request));
        if (jobId == null) {
            return findByIdempotencyKey(key);
        }
        insertWindows(jobId, request);
        return get(jobId);
    }

    public AggregationJobDTOs.AggregationJobResponse get(UUID id) {
        List<AggregationJobDTOs.AggregationJobResponse> jobs = jdbcTemplate.query("""
                select id, status, requested_from, requested_to, window_duration_seconds,
                       sensor_external_id, compartimento_id, requested_by, created_at,
                       started_at, completed_at, error, total_windows, completed_windows,
                       failed_windows, processing_windows
                from analytics_aggregation_job
                where id = ?
                """, (rs, rowNum) -> mapJob(rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("requested_from").toInstant(),
                rs.getTimestamp("requested_to").toInstant(),
                Duration.ofSeconds(rs.getLong("window_duration_seconds")),
                rs.getString("sensor_external_id"),
                rs.getString("compartimento_id"),
                rs.getString("requested_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(),
                rs.getString("error"),
                rs.getInt("total_windows"),
                rs.getInt("completed_windows"),
                rs.getInt("failed_windows"),
                rs.getInt("processing_windows")), id);
        if (jobs.isEmpty()) {
            throw new NotFoundException("Aggregation job not found id=" + id);
        }
        return withWindows(jobs.getFirst());
    }

    private AggregationJobDTOs.AggregationJobResponse findByIdempotencyKey(String key) {
        UUID id = jdbcTemplate.queryForObject(
                "select id from analytics_aggregation_job where idempotency_key = ?",
                UUID.class,
                key
        );
        return get(id);
    }

    private AggregationJobDTOs.AggregationJobResponse withWindows(AggregationJobDTOs.AggregationJobResponse job) {
        List<AggregationJobDTOs.AggregationWindowResponse> windows = jdbcTemplate.query("""
                select id, window_index, window_from, window_to, status, attempts, next_attempt_at,
                       locked_at, locked_by, started_at, completed_at, error
                from analytics_aggregation_window
                where job_id = ?
                order by window_index asc
                """, (rs, rowNum) -> new AggregationJobDTOs.AggregationWindowResponse(
                rs.getObject("id", UUID.class),
                rs.getInt("window_index"),
                rs.getTimestamp("window_from").toInstant(),
                rs.getTimestamp("window_to").toInstant(),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getTimestamp("next_attempt_at").toInstant(),
                rs.getTimestamp("locked_at") == null ? null : rs.getTimestamp("locked_at").toInstant(),
                rs.getString("locked_by"),
                rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(),
                rs.getString("error")
        ), job.id());
        return new AggregationJobDTOs.AggregationJobResponse(
                job.id(),
                job.status(),
                job.from(),
                job.to(),
                job.windowDuration(),
                job.sensorExternalId(),
                job.compartimentoId(),
                job.requestedBy(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt(),
                job.error(),
                job.progress(),
                windows
        );
    }

    private AggregationJobDTOs.AggregationJobResponse mapJob(
            UUID id,
            String status,
            Instant from,
            Instant to,
            Duration windowDuration,
            String sensorExternalId,
            String compartimentoId,
            String requestedBy,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            String error,
            int totalWindows,
            int completedWindows,
            int failedWindows,
            int processingWindows
    ) {
        return new AggregationJobDTOs.AggregationJobResponse(
                id,
                status,
                from,
                to,
                windowDuration,
                sensorExternalId,
                compartimentoId,
                requestedBy,
                createdAt,
                startedAt,
                completedAt,
                error,
                new AggregationJobDTOs.Progress(totalWindows, completedWindows, failedWindows, processingWindows),
                List.of()
        );
    }

    private UUID insertJob(
            AggregationJobDTOs.CreateAggregationJobRequest request,
            String requestedBy,
            String sensorExternalId,
            String compartimentoId,
            String key,
            int totalWindows
    ) {
        List<UUID> ids = jdbcTemplate.query("""
                insert into analytics_aggregation_job
                (idempotency_key, requested_from, requested_to, window_duration_seconds,
                 sensor_external_id, compartimento_id, requested_by, created_at, status, total_windows)
                values (?, ?, ?, ?, ?, ?, ?, now(), ?, ?)
                on conflict (idempotency_key) do nothing
                returning id
                """, (rs, rowNum) -> rs.getObject("id", UUID.class),
                key,
                Timestamp.from(request.from()),
                Timestamp.from(request.to()),
                request.windowDuration().toSeconds(),
                sensorExternalId,
                compartimentoId,
                requestedBy,
                AggregationJobStatus.PENDING.name(),
                totalWindows
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void insertWindows(UUID jobId, AggregationJobDTOs.CreateAggregationJobRequest request) {
        Instant cursor = request.from();
        int index = 0;
        while (cursor.isBefore(request.to())) {
            Instant end = cursor.plus(request.windowDuration());
            if (end.isAfter(request.to())) {
                end = request.to();
            }
            jdbcTemplate.update("""
                    insert into analytics_aggregation_window
                    (job_id, window_index, window_from, window_to, status, next_attempt_at)
                    values (?, ?, ?, ?, ?, now())
                    """, jobId, index, Timestamp.from(cursor), Timestamp.from(end), AggregationJobStatus.PENDING.name());
            cursor = end;
            index++;
        }
    }

    private void validate(AggregationJobDTOs.CreateAggregationJobRequest request) {
        if (request.from() == null) {
            throw new IllegalArgumentException("from is required");
        }
        if (request.to() == null) {
            throw new IllegalArgumentException("to is required");
        }
        if (!request.from().isBefore(request.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (request.windowDuration() == null || request.windowDuration().isZero() || request.windowDuration().isNegative()) {
            throw new IllegalArgumentException("windowDuration must be positive");
        }
        if (request.windowDuration().compareTo(properties.getMinWindow()) < 0) {
            throw new IllegalArgumentException("windowDuration is below the minimum allowed");
        }
        if (request.windowDuration().compareTo(properties.getMaxWindow()) > 0) {
            throw new IllegalArgumentException("windowDuration is above the maximum allowed");
        }
        Duration interval = Duration.between(request.from(), request.to());
        if (interval.compareTo(properties.getMaxInterval()) > 0) {
            throw new IllegalArgumentException("requested interval is above the maximum allowed");
        }
        if (windowCount(request) > properties.getMaxWindows()) {
            throw new IllegalArgumentException("requested interval creates too many windows");
        }
    }

    private int windowCount(AggregationJobDTOs.CreateAggregationJobRequest request) {
        long intervalSeconds = Duration.between(request.from(), request.to()).toSeconds();
        long windowSeconds = request.windowDuration().toSeconds();
        return Math.toIntExact((intervalSeconds + windowSeconds - 1) / windowSeconds);
    }

    private void ensureReferences(String sensorExternalId, String compartimentoId) {
        if (sensorExternalId != null && !sensorRepository.existsById(sensorExternalId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "sensorExternalId not found");
        }
        if (compartimentoId != null && !compartimentoRepository.existsById(compartimentoId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "compartimentoId not found");
        }
    }

    private String idempotencyKey(
            Instant from,
            Instant to,
            Duration windowDuration,
            String sensorExternalId,
            String compartimentoId
    ) {
        String canonical = from + "|" + to + "|" + windowDuration.toSeconds() + "|"
                + nullToEmpty(sensorExternalId) + "|" + nullToEmpty(compartimentoId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create aggregation idempotency key", ex);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
