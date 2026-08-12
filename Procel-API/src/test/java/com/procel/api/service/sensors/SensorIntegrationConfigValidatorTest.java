package com.procel.api.service.sensors;

import com.procel.api.config.SensorIntegrationParserProperties;
import com.procel.api.dto.sensors.SensorIntegrationAdminDTOs;
import com.procel.api.entity.sensors.SensorResolutionMode;
import com.procel.api.exception.ApiStatusException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorIntegrationConfigValidatorTest {
    private final SensorIntegrationParserProperties properties = new SensorIntegrationParserProperties();
    private final SensorIntegrationConfigValidator validator = new SensorIntegrationConfigValidator(properties);

    @Test
    void rejectsInvalidPointerDuplicateDestinationAndTooManyMappings() {
        assertThatThrownBy(() -> validator.validateVersion(request("bad", mappings("temperature_c"))))
                .isInstanceOf(ApiStatusException.class);

        assertThatThrownBy(() -> validator.validateVersion(request("/device/id", List.of(
                new SensorIntegrationAdminDTOs.MappingRequest("temperature_c", "/a", true),
                new SensorIntegrationAdminDTOs.MappingRequest("temperature_c", "/b", true)
        )))).isInstanceOf(ApiStatusException.class);

        properties.setMaxMappings(1);
        assertThatThrownBy(() -> validator.validateVersion(request("/device/id", List.of(
                new SensorIntegrationAdminDTOs.MappingRequest("a", "/a", true),
                new SensorIntegrationAdminDTOs.MappingRequest("b", "/b", true)
        )))).isInstanceOf(ApiStatusException.class);
    }

    private SensorIntegrationAdminDTOs.ParserVersionRequest request(
            String sensorPointer,
            List<SensorIntegrationAdminDTOs.MappingRequest> mappings
    ) {
        return new SensorIntegrationAdminDTOs.ParserVersionRequest(
                SensorResolutionMode.PAYLOAD_POINTER,
                "/meta/id",
                sensorPointer,
                "/measuredAt",
                null,
                "ISO_INSTANT",
                mappings
        );
    }

    private List<SensorIntegrationAdminDTOs.MappingRequest> mappings(String name) {
        return new ArrayList<>(List.of(new SensorIntegrationAdminDTOs.MappingRequest(name, "/value", true)));
    }
}
