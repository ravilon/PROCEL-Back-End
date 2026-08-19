package com.procel.telemetry.service.canonical;

import com.procel.telemetry.entity.RawTelemetryEvent;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SensorIntegrationProfileSelector {
    public CanonicalApiDTOs.ProfileSnapshot select(
            RawTelemetryEvent event,
            CanonicalApiDTOs.SnapshotResponse snapshot
    ) {
        List<CanonicalApiDTOs.ProfileSnapshot> candidates = snapshot.profiles().stream()
                .filter(profile -> profile.source() == event.getSource())
                .filter(profile -> matchesSensor(event, profile))
                .toList();
        if (candidates.isEmpty()) {
            throw new ProfileSelectionException("PROFILE_NOT_FOUND", "No active integration profile matches the raw event.");
        }
        if (candidates.size() > 1) {
            throw new ProfileSelectionException("PROFILE_AMBIGUOUS", "More than one active integration profile matches the raw event.");
        }
        var selected = candidates.getFirst();
        if (selected.activeParserVersion().sensorResolutionMode() == CanonicalApiDTOs.SensorResolutionMode.ROUTE_SENSOR
                && (event.getSensorId() == null || event.getSensorId().isBlank())) {
            throw new ProfileSelectionException("SENSOR_ROUTE_REQUIRED", "sensorId is required for ROUTE_SENSOR parser.");
        }
        return selected;
    }

    private boolean matchesSensor(RawTelemetryEvent event, CanonicalApiDTOs.ProfileSnapshot profile) {
        if (event.getSensorId() == null || event.getSensorId().isBlank()) {
            return true;
        }
        return profile.bindings().stream()
                .anyMatch(binding -> event.getSensorId().equals(binding.sensorExternalId()));
    }
}
