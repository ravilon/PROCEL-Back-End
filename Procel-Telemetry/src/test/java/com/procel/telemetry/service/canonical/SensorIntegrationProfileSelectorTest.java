package com.procel.telemetry.service.canonical;

import com.procel.telemetry.entity.RawTelemetryEvent;
import com.procel.telemetry.entity.TelemetrySource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorIntegrationProfileSelectorTest {
    private final SensorIntegrationProfileSelector selector = new SensorIntegrationProfileSelector();

    @Test
    void selectsZeroUniqueAndAmbiguousProfiles() {
        RawTelemetryEvent event = event("sensor-1");
        assertThatThrownBy(() -> selector.select(event, snapshot(List.of())))
                .isInstanceOfSatisfying(ProfileSelectionException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PROFILE_NOT_FOUND"));

        var selected = selector.select(event, snapshot(List.of(profile("p1", "v1", "sensor-1",
                CanonicalApiDTOs.SensorResolutionMode.PAYLOAD_POINTER))));
        assertThat(selected.id()).isEqualTo("p1");
        assertThat(selected.activeParserVersion().id()).isEqualTo("v1");

        assertThatThrownBy(() -> selector.select(event, snapshot(List.of(
                profile("p1", "v1", "sensor-1", CanonicalApiDTOs.SensorResolutionMode.PAYLOAD_POINTER),
                profile("p2", "v2", "sensor-1", CanonicalApiDTOs.SensorResolutionMode.PAYLOAD_POINTER)
        )))).isInstanceOfSatisfying(ProfileSelectionException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("PROFILE_AMBIGUOUS"));
    }

    @Test
    void routeSensorRequiresRawSensorId() {
        RawTelemetryEvent event = event(null);
        assertThatThrownBy(() -> selector.select(event, snapshot(List.of(
                profile("p1", "v1", "sensor-1", CanonicalApiDTOs.SensorResolutionMode.ROUTE_SENSOR)
        )))).isInstanceOfSatisfying(ProfileSelectionException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("SENSOR_ROUTE_REQUIRED"));
    }

    private RawTelemetryEvent event(String sensorId) {
        return new RawTelemetryEvent(
                "producer",
                TelemetrySource.REST,
                "msg",
                sensorId,
                Instant.parse("2026-08-19T12:00:00Z"),
                Instant.parse("2026-08-19T12:00:01Z"),
                java.util.Map.of("value", 1),
                "hash",
                Instant.parse("2026-08-20T12:00:00Z")
        );
    }

    private CanonicalApiDTOs.SnapshotResponse snapshot(List<CanonicalApiDTOs.ProfileSnapshot> profiles) {
        return new CanonicalApiDTOs.SnapshotResponse(1, Instant.now(), profiles);
    }

    private CanonicalApiDTOs.ProfileSnapshot profile(
            String profileId,
            String versionId,
            String sensorId,
            CanonicalApiDTOs.SensorResolutionMode mode
    ) {
        return new CanonicalApiDTOs.ProfileSnapshot(
                profileId,
                profileId,
                TelemetrySource.REST,
                new CanonicalApiDTOs.ParserVersionSnapshot(
                        versionId,
                        1,
                        mode,
                        "/meta/id",
                        mode == CanonicalApiDTOs.SensorResolutionMode.PAYLOAD_POINTER ? "/device/id" : null,
                        "/ts",
                        null,
                        "ISO_INSTANT",
                        List.of()
                ),
                List.of(new CanonicalApiDTOs.BindingSnapshot(sensorId, "Sensor"))
        );
    }
}
