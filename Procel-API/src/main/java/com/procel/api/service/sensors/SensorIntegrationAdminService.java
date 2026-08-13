package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.SensorIntegrationAdminDTOs;
import com.procel.api.entity.sensors.*;
import com.procel.api.exception.ApiStatusException;
import com.procel.api.exception.ConflictException;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.sensors.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SensorIntegrationAdminService {
    private final SensorIntegrationProfileRepository profileRepo;
    private final SensorIntegrationParserVersionRepository versionRepo;
    private final SensorIntegrationBindingRepository bindingRepo;
    private final SensorRepository sensorRepo;
    private final SensorIntegrationConfigValidator validator;

    public SensorIntegrationAdminService(
            SensorIntegrationProfileRepository profileRepo,
            SensorIntegrationParserVersionRepository versionRepo,
            SensorIntegrationBindingRepository bindingRepo,
            SensorRepository sensorRepo,
            SensorIntegrationConfigValidator validator
    ) {
        this.profileRepo = profileRepo;
        this.versionRepo = versionRepo;
        this.bindingRepo = bindingRepo;
        this.sensorRepo = sensorRepo;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<SensorIntegrationAdminDTOs.ProfileResponse> listProfiles(boolean includeInactive) {
        return profileRepo.findAll().stream()
                .filter(profile -> includeInactive || profile.isAtivo())
                .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getNome(), right.getNome()))
                .map(this::toProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public SensorIntegrationAdminDTOs.ProfileResponse getProfile(UUID profileId) {
        return toProfile(findProfile(profileId));
    }

    @Transactional
    public SensorIntegrationAdminDTOs.ProfileResponse createProfile(SensorIntegrationAdminDTOs.ProfileRequest request) {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("nome is required");
        }
        if (request.source() == null) throw new IllegalArgumentException("source is required");
        String nome = request.nome().trim();
        profileRepo.findByNome(nome).ifPresent(existing -> {
            throw new ConflictException("Integration profile already exists nome=" + nome);
        });
        return toProfile(profileRepo.save(new SensorIntegrationProfile(nome, blankToNull(request.descricao()), request.source())));
    }

    @Transactional
    public SensorIntegrationAdminDTOs.ProfileResponse updateProfile(UUID profileId, SensorIntegrationAdminDTOs.ProfileUpdateRequest request) {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("nome is required");
        }
        SensorIntegrationProfile profile = findProfile(profileId);
        String nome = request.nome().trim();
        profileRepo.findByNome(nome)
                .filter(existing -> !existing.getId().equals(profileId))
                .ifPresent(existing -> {
                    throw new ConflictException("Integration profile already exists nome=" + nome);
                });
        boolean published = versionRepo.existsByProfile_IdAndStatusIn(profileId, List.of(
                SensorIntegrationParserStatus.ACTIVE,
                SensorIntegrationParserStatus.INACTIVE
        ));
        if (published) {
            if (request.source() != null && request.source() != profile.getSource()) {
                throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "PROFILE_SOURCE_IMMUTABLE", "Profile source is immutable after first publication.");
            }
            profile.updatePublished(nome, blankToNull(request.descricao()));
        } else {
            if (request.source() == null) throw new IllegalArgumentException("source is required");
            profile.update(nome, blankToNull(request.descricao()), request.source());
        }
        return toProfile(profileRepo.save(profile));
    }

    @Transactional
    public SensorIntegrationAdminDTOs.ProfileResponse activateProfile(UUID profileId) {
        SensorIntegrationProfile profile = findProfile(profileId);
        profile.activate();
        return toProfile(profileRepo.save(profile));
    }

    @Transactional
    public void deactivateProfile(UUID profileId) {
        SensorIntegrationProfile profile = findProfile(profileId);
        profile.deactivate();
        profileRepo.save(profile);
    }

    @Transactional
    public SensorIntegrationAdminDTOs.ParserVersionResponse createVersion(UUID profileId, SensorIntegrationAdminDTOs.ParserVersionRequest request) {
        validator.validateVersion(request);
        SensorIntegrationProfile profile = findProfile(profileId);
        int next = versionRepo.maxVersionByProfile(profileId) + 1;
        SensorIntegrationParserVersion version = new SensorIntegrationParserVersion(
                profile,
                next,
                request.sensorResolutionMode(),
                request.messageIdPointer().trim(),
                blankToNull(request.sensorExternalIdPointer()),
                request.timestampPointer().trim(),
                blankToNull(request.sourceReceivedAtPointer())
        );
        version.replaceMappings(toMappings(request));
        return toVersion(versionRepo.save(version));
    }

    @Transactional
    public SensorIntegrationAdminDTOs.ParserVersionResponse updateVersion(UUID profileId, UUID versionId, SensorIntegrationAdminDTOs.ParserVersionRequest request) {
        validator.validateVersion(request);
        SensorIntegrationParserVersion version = findVersion(profileId, versionId);
        if (version.getStatus() != SensorIntegrationParserStatus.DRAFT) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_IMMUTABLE", "Only DRAFT parser versions can be edited.");
        }
        version.updateDraft(
                request.sensorResolutionMode(),
                request.messageIdPointer().trim(),
                blankToNull(request.sensorExternalIdPointer()),
                request.timestampPointer().trim(),
                blankToNull(request.sourceReceivedAtPointer())
        );
        version.replaceMappings(toMappings(request));
        return toVersion(versionRepo.save(version));
    }

    @Transactional(readOnly = true)
    public List<SensorIntegrationAdminDTOs.ParserVersionResponse> listVersions(UUID profileId) {
        findProfile(profileId);
        return versionRepo.findAllByProfile_IdOrderByVersionDesc(profileId).stream().map(this::toVersion).toList();
    }

    @Transactional(readOnly = true)
    public SensorIntegrationAdminDTOs.ParserVersionResponse getVersion(UUID profileId, UUID versionId) {
        return toVersion(findVersion(profileId, versionId));
    }

    @Transactional
    public SensorIntegrationAdminDTOs.BindingResponse createBinding(UUID profileId, SensorIntegrationAdminDTOs.BindingRequest request) {
        if (request == null || request.sensorExternalId() == null || request.sensorExternalId().isBlank()) {
            throw new IllegalArgumentException("sensorExternalId is required");
        }
        SensorIntegrationProfile profile = findProfile(profileId);
        if (!profile.isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "PROFILE_INACTIVE", "Integration profile is inactive.");
        }
        Sensor sensor = sensorRepo.findByExternalId(request.sensorExternalId().trim())
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "SENSOR_NOT_FOUND", "Sensor not found."));
        if (!sensor.isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SENSOR_INACTIVE", "Sensor is inactive.");
        }
        bindingRepo.findByProfile_IdAndSensor_ExternalIdAndAtivoTrue(profileId, sensor.getExternalId()).ifPresent(existing -> {
            throw new ApiStatusException(HttpStatus.CONFLICT, "BINDING_ALREADY_ACTIVE", "Binding is already active.");
        });
        return toBinding(bindingRepo.save(new SensorIntegrationBinding(sensor, profile)));
    }

    @Transactional(readOnly = true)
    public List<SensorIntegrationAdminDTOs.BindingResponse> listBindings(UUID profileId, boolean includeInactive) {
        findProfile(profileId);
        var bindings = includeInactive
                ? bindingRepo.findAllByProfile_IdOrderByCreatedAtDesc(profileId)
                : bindingRepo.findAllByProfile_IdAndAtivoTrueOrderByCreatedAtDesc(profileId);
        return bindings.stream().map(this::toBinding).toList();
    }

    @Transactional
    public SensorIntegrationAdminDTOs.BindingResponse activateBinding(UUID bindingId) {
        SensorIntegrationBinding binding = findBinding(bindingId);
        if (binding.isAtivo()) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "BINDING_ALREADY_ACTIVE", "Binding is already active.");
        }
        if (!binding.getProfile().isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "PROFILE_INACTIVE", "Integration profile is inactive.");
        }
        if (!binding.getSensor().isAtivo()) {
            throw new ApiStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "SENSOR_INACTIVE", "Sensor is inactive.");
        }
        bindingRepo.findByProfile_IdAndSensor_ExternalIdAndAtivoTrue(
                binding.getProfile().getId(),
                binding.getSensor().getExternalId()
        ).ifPresent(existing -> {
            throw new ApiStatusException(HttpStatus.CONFLICT, "BINDING_ALREADY_ACTIVE_FOR_SENSOR_PROFILE", "Another active binding already exists.");
        });
        binding.activate();
        return toBinding(bindingRepo.save(binding));
    }

    @Transactional
    public void deactivateBinding(UUID bindingId) {
        SensorIntegrationBinding binding = findBinding(bindingId);
        if (!binding.isAtivo()) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "BINDING_ALREADY_INACTIVE", "Binding is already inactive.");
        }
        binding.deactivate(Instant.now());
        bindingRepo.save(binding);
    }

    private SensorIntegrationProfile findProfile(UUID profileId) {
        if (profileId == null) throw new IllegalArgumentException("profileId is required");
        return profileRepo.findById(profileId).orElseThrow(() -> new NotFoundException("Integration profile not found id=" + profileId));
    }

    private SensorIntegrationParserVersion findVersion(UUID profileId, UUID versionId) {
        findProfile(profileId);
        SensorIntegrationParserVersion version = versionRepo.findById(versionId)
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "PARSER_VERSION_NOT_FOUND", "Parser version not found."));
        if (!version.getProfile().getId().equals(profileId)) {
            throw new ApiStatusException(HttpStatus.CONFLICT, "PARSER_VERSION_PROFILE_MISMATCH", "Parser version does not belong to profile.");
        }
        return version;
    }

    private SensorIntegrationBinding findBinding(UUID bindingId) {
        if (bindingId == null) throw new IllegalArgumentException("bindingId is required");
        return bindingRepo.findById(bindingId)
                .orElseThrow(() -> new ApiStatusException(HttpStatus.NOT_FOUND, "BINDING_NOT_FOUND", "Binding not found."));
    }

    private List<SensorIntegrationValueMapping> toMappings(SensorIntegrationAdminDTOs.ParserVersionRequest request) {
        return request.valueMappings().stream()
                .map(mapping -> new SensorIntegrationValueMapping(
                        mapping.parameterName().trim(),
                        mapping.valuePointer().trim(),
                        mapping.required()
                ))
                .toList();
    }

    public SensorIntegrationAdminDTOs.ProfileResponse toProfile(SensorIntegrationProfile profile) {
        return new SensorIntegrationAdminDTOs.ProfileResponse(
                profile.getId(), profile.getNome(), profile.getDescricao(), profile.getSource(),
                profile.isAtivo(), profile.getCreatedAt(), profile.getUpdatedAt()
        );
    }

    public SensorIntegrationAdminDTOs.ParserVersionResponse toVersion(SensorIntegrationParserVersion version) {
        return new SensorIntegrationAdminDTOs.ParserVersionResponse(
                version.getId(),
                version.getProfile().getId(),
                version.getVersion(),
                version.getStatus(),
                version.getSensorResolutionMode(),
                version.getMessageIdPointer(),
                version.getSensorExternalIdPointer(),
                version.getTimestampPointer(),
                version.getSourceReceivedAtPointer(),
                version.getTimestampFormat(),
                version.getCreatedAt(),
                version.getUpdatedAt(),
                version.getPublishedAt(),
                version.getValueMappings().stream()
                        .map(mapping -> new SensorIntegrationAdminDTOs.MappingResponse(
                                mapping.getId(),
                                mapping.getParameterName(),
                                mapping.getValuePointer(),
                                mapping.isRequired()
                        ))
                        .toList()
        );
    }

    public SensorIntegrationAdminDTOs.BindingResponse toBinding(SensorIntegrationBinding binding) {
        return new SensorIntegrationAdminDTOs.BindingResponse(
                binding.getId(),
                binding.getProfile().getId(),
                binding.getSensor().getExternalId(),
                binding.getSensor().getNome(),
                binding.isAtivo(),
                binding.getCreatedAt(),
                binding.getDeactivatedAt()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
