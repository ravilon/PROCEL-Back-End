package com.procel.api.service.sensors;

import com.fasterxml.jackson.core.JsonPointer;
import com.procel.api.config.SensorIntegrationParserProperties;
import com.procel.api.dto.sensors.SensorIntegrationAdminDTOs;
import com.procel.api.entity.sensors.SensorResolutionMode;
import com.procel.api.exception.ApiStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
public class SensorIntegrationConfigValidator {
    private final SensorIntegrationParserProperties properties;

    public SensorIntegrationConfigValidator(SensorIntegrationParserProperties properties) {
        this.properties = properties;
    }

    public void validateVersion(SensorIntegrationAdminDTOs.ParserVersionRequest request) {
        if (request == null) throw bad("body is required");
        if (request.sensorResolutionMode() == null) throw bad("sensorResolutionMode is required");
        requirePointer(request.messageIdPointer(), "messageIdPointer");
        requirePointer(request.timestampPointer(), "timestampPointer");
        optionalPointer(request.sourceReceivedAtPointer(), "sourceReceivedAtPointer");
        if (!"ISO_INSTANT".equals(request.timestampFormat())) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "TIMESTAMP_FORMAT_INVALID", "timestampFormat must be ISO_INSTANT");
        }
        if (request.sensorResolutionMode() == SensorResolutionMode.PAYLOAD_POINTER) {
            requirePointer(request.sensorExternalIdPointer(), "sensorExternalIdPointer");
        } else if (request.sensorExternalIdPointer() != null && !request.sensorExternalIdPointer().isBlank()) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "SENSOR_POINTER_NOT_ALLOWED", "sensorExternalIdPointer is not allowed for ROUTE_SENSOR.");
        }
        if (request.valueMappings() == null || request.valueMappings().isEmpty()) {
            throw bad("valueMappings is required");
        }
        if (request.valueMappings().size() > properties.getMaxMappings()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "TOO_MANY_MAPPINGS", "Too many mappings.");
        }
        var names = new HashSet<String>();
        for (var mapping : request.valueMappings()) {
            if (mapping.parameterName() == null || mapping.parameterName().isBlank()) {
                throw bad("parameterName is required");
            }
            if (!names.add(mapping.parameterName().trim())) {
                throw new ApiStatusException(HttpStatus.BAD_REQUEST, "MAPPING_DUPLICATE", "Duplicate mapping parameterName.");
            }
            requirePointer(mapping.valuePointer(), "valuePointer");
        }
    }

    private void requirePointer(String pointer, String field) {
        if (pointer == null || pointer.isBlank()) throw bad(field + " is required");
        optionalPointer(pointer, field);
    }

    private void optionalPointer(String pointer, String field) {
        if (pointer == null || pointer.isBlank()) return;
        try {
            JsonPointer.compile(pointer);
        } catch (IllegalArgumentException ex) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "POINTER_INVALID", field + " is not a valid JSON Pointer.");
        }
    }

    private static ApiStatusException bad(String message) {
        return new ApiStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
