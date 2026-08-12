package com.procel.api.service.sensors;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.entity.sensors.MedicaoIngestaoSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadFingerprintServiceTest {
    private final PayloadFingerprintService service = new PayloadFingerprintService(JsonMapper.builder().build());

    @Test
    void normalizesDecimalsAndKeyOrderWithoutChangingJsonTypes() {
        String left = service.fingerprint(request(new LinkedHashMap<>(Map.of(
                "temperature_c", new BigDecimal("23.70"),
                "presence", true
        ))));

        LinkedHashMap<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("presence", true);
        reordered.put("temperature_c", new BigDecimal("23.7"));
        String right = service.fingerprint(request(reordered));

        assertThat(right).isEqualTo(left);
    }

    @Test
    void distinguishesNumbersTextsBooleansAndMissingFields() {
        String number = service.fingerprint(request(Map.of("value", 61)));
        String textNumber = service.fingerprint(request(Map.of("value", "61")));
        String bool = service.fingerprint(request(Map.of("value", true)));
        String textBool = service.fingerprint(request(Map.of("value", "true")));
        String explicitNull = service.fingerprint(request(mapWithNull()));
        String absent = service.fingerprint(request(Map.of()));

        assertThat(number).isNotEqualTo(textNumber);
        assertThat(bool).isNotEqualTo(textBool);
        assertThat(explicitNull).isNotEqualTo(absent);
    }

    @Test
    void ignoresSourceReceivedAtForRedeliveryFingerprint() {
        var values = Map.<String, Object>of("value", new BigDecimal("61.0"));
        var first = new SensorIngestDTOs.CanonicalIngestRequest(
                "msg-1",
                "SII-001",
                Instant.parse("2026-08-11T23:30:00Z"),
                MedicaoIngestaoSource.MQTT,
                Instant.parse("2026-08-11T23:30:02Z"),
                values
        );
        var redelivery = new SensorIngestDTOs.CanonicalIngestRequest(
                "msg-1",
                "SII-001",
                Instant.parse("2026-08-11T23:30:00Z"),
                MedicaoIngestaoSource.MQTT,
                Instant.parse("2026-08-11T23:31:02Z"),
                values
        );

        assertThat(service.fingerprint(redelivery)).isEqualTo(service.fingerprint(first));
    }

    private static SensorIngestDTOs.CanonicalIngestRequest request(Map<String, Object> values) {
        return new SensorIngestDTOs.CanonicalIngestRequest(
                "msg-1",
                "SII-001",
                Instant.parse("2026-08-11T23:30:00Z"),
                MedicaoIngestaoSource.MQTT,
                Instant.parse("2026-08-11T23:30:02Z"),
                values
        );
    }

    private static Map<String, Object> mapWithNull() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("value", null);
        return map;
    }
}
