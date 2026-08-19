package com.procel.telemetry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.entity.TelemetrySource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadHashServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayloadHashService service = new PayloadHashService(objectMapper);

    @Test
    void normalizesOnlyObjectPropertyOrderAndPreservesArrayOrder() throws Exception {
        String left = hash("""
                {"b":2,"a":{"z":true,"x":1},"array":[1,2]}
                """);
        String right = hash("""
                {"array":[1,2],"a":{"x":1,"z":true},"b":2}
                """);
        String differentArray = hash("""
                {"a":{"x":1,"z":true},"array":[2,1],"b":2}
                """);

        assertThat(left).isEqualTo(right);
        assertThat(left).isNotEqualTo(differentArray);
    }

    @Test
    void preservesJsonTypeNullAndMissingFieldDifferences() throws Exception {
        assertThat(hash("{\"value\":1}")).isNotEqualTo(hash("{\"value\":\"1\"}"));
        assertThat(hash("{\"value\":null}")).isNotEqualTo(hash("{}"));
    }

    @Test
    void includesSourceSensorIdAndSourceTimestampButNotProducerOrReceivedMetadata() throws Exception {
        var payload = objectMapper.readTree("{\"value\":1}");
        String base = service.fingerprint(
                TelemetrySource.REST,
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"s-1\"")),
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"2026-08-19T12:00:00Z\"")),
                payload
        );
        String differentSensor = service.fingerprint(
                TelemetrySource.REST,
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"s-2\"")),
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"2026-08-19T12:00:00Z\"")),
                payload
        );
        String missingSensor = service.fingerprint(
                TelemetrySource.REST,
                PayloadHashService.PresenceValue.absent(),
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"2026-08-19T12:00:00Z\"")),
                payload
        );
        String nullSensor = service.fingerprint(
                TelemetrySource.REST,
                PayloadHashService.PresenceValue.present(objectMapper.readTree("null")),
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"2026-08-19T12:00:00Z\"")),
                payload
        );
        String missingTimestamp = service.fingerprint(
                TelemetrySource.REST,
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"s-1\"")),
                PayloadHashService.PresenceValue.absent(),
                payload
        );
        String nullTimestamp = service.fingerprint(
                TelemetrySource.REST,
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"s-1\"")),
                PayloadHashService.PresenceValue.present(objectMapper.readTree("null")),
                payload
        );
        String differentTimestamp = service.fingerprint(
                TelemetrySource.REST,
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"s-1\"")),
                PayloadHashService.PresenceValue.present(objectMapper.readTree("\"2026-08-19T12:00:01Z\"")),
                payload
        );

        assertThat(base).isNotEqualTo(differentSensor);
        assertThat(missingSensor).isNotEqualTo(nullSensor);
        assertThat(missingTimestamp).isNotEqualTo(nullTimestamp);
        assertThat(base).isNotEqualTo(differentTimestamp);
    }

    private String hash(String payload) throws Exception {
        return service.fingerprint(
                TelemetrySource.REST,
                PayloadHashService.PresenceValue.absent(),
                PayloadHashService.PresenceValue.absent(),
                objectMapper.readTree(payload)
        );
    }
}
