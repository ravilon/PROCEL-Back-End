package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIngestDTOs;
import com.procel.api.dto.sensors.SensorTelemetryIngestDTOs;
import com.procel.api.entity.sensors.SensorIntegrationParserStatus;
import com.procel.api.entity.sensors.SensorResolutionMode;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.repository.sensors.SensorIntegrationBindingRepository;
import com.procel.api.repository.sensors.SensorIntegrationParserVersionRepository;
import com.procel.api.repository.sensors.SensorIntegrationProfileRepository;
import com.procel.api.repository.sensors.SensorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SensorTelemetryIntegrationIngestService {
    private final SensorIntegrationProfileRepository profileRepo;
    private final SensorIntegrationParserVersionRepository versionRepo;
    private final SensorRepository sensorRepo;
    private final SensorIntegrationBindingRepository bindingRepo;
    private final SensorExternalPayloadParser parser;
    private final SensorIngestOrchestrator orchestrator;

    public SensorTelemetryIntegrationIngestService(
            SensorIntegrationProfileRepository profileRepo,
            SensorIntegrationParserVersionRepository versionRepo,
            SensorRepository sensorRepo,
            SensorIntegrationBindingRepository bindingRepo,
            SensorExternalPayloadParser parser,
            SensorIngestOrchestrator orchestrator
    ) {
        this.profileRepo = profileRepo;
        this.versionRepo = versionRepo;
        this.sensorRepo = sensorRepo;
        this.bindingRepo = bindingRepo;
        this.parser = parser;
        this.orchestrator = orchestrator;
    }

    @Transactional(readOnly = true)
    public SensorIngestOrchestrator.IngestOutcome ingestPayload(
            UUID profileId,
            String routeSensorExternalId,
            String serviceProducerId,
            SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest request
    ) {
        request = normalized(request);
        if (request.payload() == null || !request.payload().isObject()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "payload must be a JSON object");
        }

        var profile = profileRepo.findById(profileId)
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Integration profile not found."));
        if (!profile.isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "PROFILE_INACTIVE", "Integration profile is inactive.");
        }
        var version = versionRepo.findByProfile_IdAndStatus(profileId, SensorIntegrationParserStatus.ACTIVE)
                .orElseThrow(() -> new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_NOT_ACTIVE", "No active parser version for profile."));
        if (routeSensorExternalId != null && version.getSensorResolutionMode() != SensorResolutionMode.ROUTE_SENSOR) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "SENSOR_RESOLUTION_CONFLICT", "Route sensor is only allowed for ROUTE_SENSOR parser.");
        }
        if (routeSensorExternalId == null && version.getSensorResolutionMode() == SensorResolutionMode.ROUTE_SENSOR) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "SENSOR_ROUTE_REQUIRED", "sensorExternalId route parameter is required.");
        }

        var parsed = parser.parse(request.payload(), version, profile.getSource(), routeSensorExternalId);
        var canonical = new SensorIngestDTOs.CanonicalIngestRequest(
                request.rawMessageId().trim(),
                parsed.sensorExternalId(),
                parsed.timestamp(),
                parsed.source(),
                parsed.sourceReceivedAt(),
                parsed.values()
        );

        var sensor = sensorRepo.findByExternalId(canonical.sensorExternalId())
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "SENSOR_NOT_FOUND", "Sensor not found."));
        if (!sensor.isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SENSOR_INACTIVE", "Sensor is inactive.");
        }
        bindingRepo.findByProfile_IdAndSensor_ExternalIdAndAtivoTrue(profileId, canonical.sensorExternalId())
                .orElseThrow(() -> {
                    var inactive = bindingRepo.findFirstByProfile_IdAndSensor_ExternalIdOrderByCreatedAtDesc(profileId, canonical.sensorExternalId());
                    return inactive.isPresent()
                            ? new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "BINDING_INACTIVE", "Binding is inactive.")
                            : new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "BINDING_NOT_FOUND", "Binding not found.");
                });

        return orchestrator.ingestTelemetryRawWithProfile(profileId, version.getId(), serviceProducerId, request, canonical);
    }

    private SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest normalized(
            SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest request
    ) {
        return new SensorTelemetryIngestDTOs.TelemetryRawIntegrationIngestRequest(
                request.rawTelemetryEventId().trim(),
                request.originalProducerId().trim(),
                request.rawMessageId().trim(),
                request.rawReceivedAt(),
                request.rawSourceTimestamp(),
                request.payload()
        );
    }
}
