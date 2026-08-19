package com.procel.api.service.analytics;

import com.procel.api.config.AnalyticsAggregationProperties;
import com.procel.api.repository.sensors.MedicaoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class CanonicalMeasurementWindowScanner implements AggregationWindowProcessor {
    private final MedicaoRepository medicaoRepository;
    private final AnalyticsAggregationProperties properties;

    public CanonicalMeasurementWindowScanner(
            MedicaoRepository medicaoRepository,
            AnalyticsAggregationProperties properties
    ) {
        this.medicaoRepository = medicaoRepository;
        this.properties = properties;
    }

    @Override
    public void process(AggregationWindowWork work) {
        int page = 0;
        while (true) {
            var ids = medicaoRepository.findIdsForAggregationWindow(
                    work.from(),
                    work.to(),
                    work.sensorExternalId(),
                    work.compartimentoId(),
                    PageRequest.of(page, properties.getMeasurementPageSize())
            );
            if (ids.isEmpty()) {
                return;
            }
            page++;
        }
    }
}
