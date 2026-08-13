package com.procel.api.service.sensors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.api.config.SensorIntegrationParserProperties;
import com.procel.api.entity.sensors.*;
import com.procel.api.exception.ApiStatusException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorExternalPayloadParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SensorIntegrationParserProperties properties = new SensorIntegrationParserProperties();
    private final SensorExternalPayloadParser parser = new SensorExternalPayloadParser(properties);

    @Test
    void parsesScalarsAndPreservesNull() throws Exception {
        var result = parser.parse(
                objectMapper.readTree("""
                        {
                          "meta": {"id": "msg-1"},
                          "device": {"id": "SII-001"},
                          "measuredAt": "2026-08-11T23:30:00Z",
                          "receivedAt": "2026-08-11T23:30:02Z",
                          "readings": {"temperature": 23.70, "presence": true, "label": "ok", "optional": null}
                        }
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, true),
                MedicaoIngestaoSource.REST,
                null
        );

        assertThat(result.messageId()).isEqualTo("msg-1");
        assertThat(result.sensorExternalId()).isEqualTo("SII-001");
        assertThat(result.timestamp()).isEqualTo(Instant.parse("2026-08-11T23:30:00Z"));
        assertThat(result.source()).isEqualTo(MedicaoIngestaoSource.REST);
        assertThat(result.sourceReceivedAt()).isEqualTo(Instant.parse("2026-08-11T23:30:02Z"));
        assertThat(result.values()).containsEntry("presence", true).containsEntry("label", "ok");
        assertThat(result.values()).containsKey("optional");
        assertThat(result.values().get("optional")).isNull();
    }

    @Test
    void optionalMissingIsOmittedButRequiredMissingFails() throws Exception {
        var payload = objectMapper.readTree("""
                {"meta":{"id":"msg-1"},"device":{"id":"SII-001"},"measuredAt":"2026-08-11T23:30:00Z","readings":{"temperature":1}}
                """);
        var result = parser.parse(payload, version(SensorResolutionMode.PAYLOAD_POINTER, false), MedicaoIngestaoSource.API, null);
        assertThat(result.values()).doesNotContainKey("optional");

        assertThatThrownBy(() -> parser.parse(
                objectMapper.readTree("""
                        {"meta":{"id":"msg-1"},"device":{"id":"SII-001"},"measuredAt":"2026-08-11T23:30:00Z","readings":{}}
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, false),
                MedicaoIngestaoSource.API,
                null
        )).isInstanceOf(ApiStatusException.class)
                .hasMessageContaining("Required mapping is missing");
    }

    @Test
    void rejectsObjectArrayInvalidTimestampAndBlankMessageId() throws Exception {
        assertThatThrownBy(() -> parser.parse(
                objectMapper.readTree("""
                        {"meta":{"id":"msg-1"},"device":{"id":"SII-001"},"measuredAt":"2026-08-11T23:30:00Z","readings":{"temperature":{"v":1}}}
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, false),
                MedicaoIngestaoSource.API,
                null
        )).isInstanceOf(ApiStatusException.class);

        assertThatThrownBy(() -> parser.parse(
                objectMapper.readTree("""
                        {"meta":{"id":"msg-1"},"device":{"id":"SII-001"},"measuredAt":"2026-08-11T23:30:00Z","readings":{"temperature":[1]}}
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, false),
                MedicaoIngestaoSource.API,
                null
        )).isInstanceOf(ApiStatusException.class);

        assertThatThrownBy(() -> parser.parse(
                objectMapper.readTree("""
                        {"meta":{"id":"msg-1"},"device":{"id":"SII-001"},"measuredAt":"bad","readings":{"temperature":1}}
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, false),
                MedicaoIngestaoSource.API,
                null
        )).isInstanceOf(ApiStatusException.class);

        assertThatThrownBy(() -> parser.parse(
                objectMapper.readTree("""
                        {"meta":{"id":""},"device":{"id":"SII-001"},"measuredAt":"2026-08-11T23:30:00Z","readings":{"temperature":1}}
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, false),
                MedicaoIngestaoSource.API,
                null
        )).isInstanceOf(ApiStatusException.class);
    }

    @Test
    void routeSensorModeRejectsPayloadSensorRouteAmbiguity() throws Exception {
        var payload = objectMapper.readTree("""
                {"meta":{"id":"msg-1"},"measuredAt":"2026-08-11T23:30:00Z","readings":{"temperature":1}}
                """);
        var result = parser.parse(payload, version(SensorResolutionMode.ROUTE_SENSOR, false), MedicaoIngestaoSource.API, "SII-ROUTE");
        assertThat(result.sensorExternalId()).isEqualTo("SII-ROUTE");

        assertThatThrownBy(() -> parser.parse(payload, version(SensorResolutionMode.ROUTE_SENSOR, false), MedicaoIngestaoSource.API, null))
                .isInstanceOf(ApiStatusException.class);
    }

    @Test
    void sourceReceivedAtCanBeAbsentButInvalidValueFails() throws Exception {
        var result = parser.parse(
                objectMapper.readTree("""
                        {"meta":{"id":"msg-1"},"device":{"id":"SII-001"},"measuredAt":"2026-08-11T23:30:00Z","readings":{"temperature":1}}
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, false),
                MedicaoIngestaoSource.REST,
                null
        );
        assertThat(result.sourceReceivedAt()).isNull();

        assertThatThrownBy(() -> parser.parse(
                objectMapper.readTree("""
                        {"meta":{"id":"msg-1"},"device":{"id":"SII-001"},"measuredAt":"2026-08-11T23:30:00Z","receivedAt":"bad","readings":{"temperature":1}}
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, false),
                MedicaoIngestaoSource.REST,
                null
        )).isInstanceOf(ApiStatusException.class);
    }

    @Test
    void rejectsPayloadTooDeep() throws Exception {
        properties.setMaxDepth(2);
        assertThatThrownBy(() -> parser.parse(
                objectMapper.readTree(""" 
                        {"meta":{"id":"msg-1"},"device":{"id":"SII-001"},"measuredAt":"2026-08-11T23:30:00Z","a":{"b":{"c":1}},"readings":{"temperature":1}}
                        """),
                version(SensorResolutionMode.PAYLOAD_POINTER, false),
                MedicaoIngestaoSource.API,
                null
        )).isInstanceOf(ApiStatusException.class);
    }

    private SensorIntegrationParserVersion version(SensorResolutionMode mode, boolean includeNull) {
        var profile = new SensorIntegrationProfile("P", null, MedicaoIngestaoSource.API);
        var version = new SensorIntegrationParserVersion(
                profile,
                1,
                mode,
                "/meta/id",
                mode == SensorResolutionMode.PAYLOAD_POINTER ? "/device/id" : null,
                "/measuredAt",
                "/receivedAt"
        );
        var mappings = new java.util.ArrayList<SensorIntegrationValueMapping>();
        mappings.add(new SensorIntegrationValueMapping("temperature_c", "/readings/temperature", true));
        mappings.add(new SensorIntegrationValueMapping("presence", "/readings/presence", false));
        mappings.add(new SensorIntegrationValueMapping("label", "/readings/label", false));
        if (includeNull) mappings.add(new SensorIntegrationValueMapping("optional", "/readings/optional", false));
        version.replaceMappings(mappings);
        return version;
    }
}
