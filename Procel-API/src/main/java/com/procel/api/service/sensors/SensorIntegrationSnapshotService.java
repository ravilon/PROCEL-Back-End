package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIntegrationSnapshotDTOs;
import com.procel.api.entity.sensors.SensorIntegrationParserStatus;
import com.procel.api.repository.sensors.SensorIntegrationBindingRepository;
import com.procel.api.repository.sensors.SensorIntegrationParserVersionRepository;
import com.procel.api.repository.sensors.SensorIntegrationProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SensorIntegrationSnapshotService {
    private final SensorIntegrationProfileRepository profileRepo;
    private final SensorIntegrationParserVersionRepository versionRepo;
    private final SensorIntegrationBindingRepository bindingRepo;

    public SensorIntegrationSnapshotService(
            SensorIntegrationProfileRepository profileRepo,
            SensorIntegrationParserVersionRepository versionRepo,
            SensorIntegrationBindingRepository bindingRepo
    ) {
        this.profileRepo = profileRepo;
        this.versionRepo = versionRepo;
        this.bindingRepo = bindingRepo;
    }

    @Transactional(readOnly = true)
    public SensorIntegrationSnapshotDTOs.SnapshotResponse snapshot() {
        var bindings = bindingRepo.findAllByAtivoTrueAndProfile_AtivoTrueAndSensor_AtivoTrue();
        var profiles = profileRepo.findAll().stream()
                .filter(profile -> profile.isAtivo())
                .flatMap(profile -> versionRepo.findByProfile_IdAndStatus(profile.getId(), SensorIntegrationParserStatus.ACTIVE)
                        .stream()
                        .map(active -> {
                            List<SensorIntegrationSnapshotDTOs.BindingSnapshot> profileBindings = bindings.stream()
                                    .filter(binding -> binding.getProfile().getId().equals(profile.getId()))
                                    .map(binding -> new SensorIntegrationSnapshotDTOs.BindingSnapshot(
                                            binding.getSensor().getExternalId(),
                                            binding.getSensor().getNome()
                                    ))
                                    .toList();
                            return new SensorIntegrationSnapshotDTOs.ProfileSnapshot(
                                    profile.getId(),
                                    profile.getNome(),
                                    profile.getSource(),
                                    new SensorIntegrationSnapshotDTOs.ParserVersionSnapshot(
                                            active.getId(),
                                            active.getVersion(),
                                            active.getSensorResolutionMode(),
                                            active.getMessageIdPointer(),
                                            active.getSensorExternalIdPointer(),
                                            active.getTimestampPointer(),
                                            active.getSourceReceivedAtPointer(),
                                            active.getTimestampFormat(),
                                            active.getValueMappings().stream()
                                                    .map(mapping -> new SensorIntegrationSnapshotDTOs.MappingSnapshot(
                                                            mapping.getParameterName(),
                                                            mapping.getValuePointer(),
                                                            mapping.isRequired()
                                                    ))
                                                    .toList()
                                    ),
                                    profileBindings
                            );
                        }))
                .toList();
        return new SensorIntegrationSnapshotDTOs.SnapshotResponse(1, Instant.now(), profiles);
    }
}
