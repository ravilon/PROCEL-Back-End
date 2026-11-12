package com.proben.api.service.sensors;

import com.proben.api.dto.sensors.SensorIntegrationAdminDTOs;
import com.proben.api.entity.sensors.SensorIntegrationParserStatus;
import com.proben.api.exception.ApiStatusException;
import com.proben.api.exception.NotFoundException;
import com.proben.api.repository.sensors.SensorIntegrationParserVersionRepository;
import com.proben.api.repository.sensors.SensorIntegrationProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Servico que implementa as operacoes de negocio de sensor integration activation.
 */
@Service
public class SensorIntegrationActivationService {
    private final SensorIntegrationProfileRepository profileRepo;
    private final SensorIntegrationParserVersionRepository versionRepo;
    private final SensorIntegrationAdminService adminService;

    public SensorIntegrationActivationService(
            SensorIntegrationProfileRepository profileRepo,
            SensorIntegrationParserVersionRepository versionRepo,
            SensorIntegrationAdminService adminService
    ) {
        this.profileRepo = profileRepo;
        this.versionRepo = versionRepo;
        this.adminService = adminService;
    }

    @Transactional
    public SensorIntegrationAdminDTOs.ParserVersionResponse activateVersion(
            UUID profileId,
            UUID versionId,
            UUID expectedActiveVersionId
    ) {
        var profile = profileRepo.findByIdForUpdate(profileId)
                .orElseThrow(() -> new NotFoundException("Integration profile not found id=" + profileId));
        var currentActive = versionRepo.findByProfile_IdAndStatus(profileId, SensorIntegrationParserStatus.ACTIVE);
        UUID currentActiveId = currentActive.map(active -> active.getId()).orElse(null);
        if (!java.util.Objects.equals(currentActiveId, expectedActiveVersionId)) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "PARSER_ACTIVATION_CONFLICT", "Active parser version changed.");
        }
        var version = versionRepo.findById(versionId)
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "PARSER_VERSION_NOT_FOUND", "Parser version not found."));
        if (!version.getProfile().getId().equals(profile.getId())) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_PROFILE_MISMATCH", "Parser version does not belong to profile.");
        }
        if (version.getStatus() != SensorIntegrationParserStatus.DRAFT) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_NOT_DRAFT", "Only DRAFT parser versions can be activated.");
        }
        Instant now = Instant.now();
        currentActive.ifPresent(active -> active.inactivate(now));
        version.activate(now);
        versionRepo.save(version);
        currentActive.ifPresent(versionRepo::save);
        return adminService.toVersion(version);
    }
}
