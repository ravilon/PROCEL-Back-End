package com.procel.api.service.sensors;

import com.procel.api.config.SensorIntegrationParserProperties;
import com.procel.api.dto.sensors.SensorIntegrationAdminDTOs;
import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.sensors.*;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.repository.sensors.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SensorIntegrationAdminServiceTest {
    @Test
    void sourceIsImmutableAfterFirstPublication() {
        var repos = repos();
        var service = service(repos);
        var profile = profile("Profile", MedicaoIngestaoSource.REST);
        when(repos.profileRepo.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(repos.profileRepo.findByNome("Profile 2")).thenReturn(Optional.empty());
        when(repos.versionRepo.existsByProfile_IdAndStatusIn(eq(profile.getId()), any())).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(
                profile.getId(),
                new SensorIntegrationAdminDTOs.ProfileUpdateRequest("Profile 2", null, MedicaoIngestaoSource.MQTT)
        )).isInstanceOf(ApiStatusException.class);
    }

    @Test
    void onlyDraftVersionCanBeEdited() {
        var repos = repos();
        var service = service(repos);
        var profile = profile("Profile", MedicaoIngestaoSource.REST);
        var active = version(profile, SensorIntegrationParserStatus.ACTIVE);
        var inactive = version(profile, SensorIntegrationParserStatus.INACTIVE);
        when(repos.profileRepo.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(repos.versionRepo.findById(active.getId())).thenReturn(Optional.of(active));
        when(repos.versionRepo.findById(inactive.getId())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.updateVersion(profile.getId(), active.getId(), request()))
                .isInstanceOf(ApiStatusException.class);
        assertThatThrownBy(() -> service.updateVersion(profile.getId(), inactive.getId(), request()))
                .isInstanceOf(ApiStatusException.class);
    }

    @Test
    void bindingLifecycleRejectsDuplicateAndRestoresDeactivatedAt() {
        var repos = repos();
        var service = service(repos);
        var profile = profile("Profile", MedicaoIngestaoSource.REST);
        var sensor = new Sensor("SII-001", "Sensor", new TipoDeSensor("T"), new Compartimento("R", null, null, "Sala", "Sala"));
        var inactive = new SensorIntegrationBinding(sensor, profile);
        UUID bindingId = UUID.randomUUID();
        ReflectionTestUtils.setField(inactive, "id", bindingId);
        inactive.deactivate(Instant.now());

        when(repos.bindingRepo.findById(bindingId)).thenReturn(Optional.of(inactive));
        when(repos.bindingRepo.findByProfile_IdAndSensor_ExternalIdAndAtivoTrue(profile.getId(), sensor.getExternalId()))
                .thenReturn(Optional.empty());
        when(repos.bindingRepo.save(any(SensorIntegrationBinding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var activated = service.activateBinding(bindingId);
        assertThat(activated.ativo()).isTrue();
        assertThat(activated.deactivatedAt()).isNull();

        assertThatThrownBy(() -> service.activateBinding(bindingId))
                .isInstanceOf(ApiStatusException.class);

        service.deactivateBinding(bindingId);
        assertThat(inactive.isAtivo()).isFalse();
        assertThat(inactive.getDeactivatedAt()).isNotNull();
        assertThatThrownBy(() -> service.deactivateBinding(bindingId))
                .isInstanceOf(ApiStatusException.class);
    }

    private SensorIntegrationAdminService service(Repos repos) {
        return new SensorIntegrationAdminService(
                repos.profileRepo,
                repos.versionRepo,
                repos.bindingRepo,
                repos.sensorRepo,
                new SensorIntegrationConfigValidator(new SensorIntegrationParserProperties())
        );
    }

    private Repos repos() {
        return new Repos(
                mock(SensorIntegrationProfileRepository.class),
                mock(SensorIntegrationParserVersionRepository.class),
                mock(SensorIntegrationBindingRepository.class),
                mock(SensorRepository.class)
        );
    }

    private SensorIntegrationProfile profile(String name, MedicaoIngestaoSource source) {
        var profile = new SensorIntegrationProfile(name, null, source);
        ReflectionTestUtils.setField(profile, "id", UUID.randomUUID());
        return profile;
    }

    private SensorIntegrationParserVersion version(SensorIntegrationProfile profile, SensorIntegrationParserStatus status) {
        var version = new SensorIntegrationParserVersion(
                profile,
                1,
                SensorResolutionMode.PAYLOAD_POINTER,
                "/id",
                "/sensor",
                "/ts",
                null
        );
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        if (status == SensorIntegrationParserStatus.ACTIVE) {
            version.activate(Instant.now());
        } else if (status == SensorIntegrationParserStatus.INACTIVE) {
            version.activate(Instant.now());
            version.inactivate(Instant.now());
        }
        return version;
    }

    private SensorIntegrationAdminDTOs.ParserVersionRequest request() {
        return new SensorIntegrationAdminDTOs.ParserVersionRequest(
                SensorResolutionMode.PAYLOAD_POINTER,
                "/id",
                "/sensor",
                "/ts",
                null,
                "ISO_INSTANT",
                List.of(new SensorIntegrationAdminDTOs.MappingRequest("temperature_c", "/value", true))
        );
    }

    private record Repos(
            SensorIntegrationProfileRepository profileRepo,
            SensorIntegrationParserVersionRepository versionRepo,
            SensorIntegrationBindingRepository bindingRepo,
            SensorRepository sensorRepo
    ) {}
}
