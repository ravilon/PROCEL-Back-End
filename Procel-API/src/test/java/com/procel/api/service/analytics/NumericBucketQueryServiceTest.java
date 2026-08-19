package com.procel.api.service.analytics;

import com.procel.api.config.AnalyticsBucketQueryProperties;
import com.procel.api.dto.analytics.NumericBucketDTOs;
import com.procel.api.repository.rooms.CompartimentoRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.repository.sensors.SensorRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NumericBucketQueryServiceTest {
    @Test
    void summarySqlUsesOnlyPersistedBucketsAndDimensionTables() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<NumericBucketDTOs.NumericBucketSummaryResponse> emptySummary = List.of();
        when(jdbcTemplate.query(
                any(String.class),
                anySummaryRowMapper(),
                any(Object[].class)
        )).thenReturn(emptySummary);
        AnalyticsBucketQueryProperties properties = new AnalyticsBucketQueryProperties();
        NumericBucketQueryService service = new NumericBucketQueryService(
                jdbcTemplate,
                mock(SensorRepository.class),
                mock(ParametroDefRepository.class),
                mock(CompartimentoRepository.class),
                properties
        );

        service.summary(new NumericBucketQuery(
                Instant.parse("2026-08-19T00:00:00Z"),
                Instant.parse("2026-08-19T00:10:00Z"),
                null,
                null,
                null,
                null,
                0,
                1
        ));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), anySummaryRowMapper(), any(Object[].class));
        assertThat(sql.getValue()).contains("analytics_numeric_bucket");
        assertThat(sql.getValue()).doesNotContain(" medicao").doesNotContain("parametro_valor");
    }

    private static RowMapper<NumericBucketDTOs.NumericBucketSummaryResponse> anySummaryRowMapper() {
        return any();
    }
}
