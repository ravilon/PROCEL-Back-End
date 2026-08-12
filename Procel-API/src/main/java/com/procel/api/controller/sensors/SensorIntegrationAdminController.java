package com.procel.api.controller.sensors;

import com.procel.api.dto.sensors.SensorIntegrationAdminDTOs;
import com.procel.api.service.sensors.SensorIntegrationActivationService;
import com.procel.api.service.sensors.SensorIntegrationAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sensor-integrations")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Sensor integrations", description = "Perfis, parsers e vinculos de integracao de sensores.")
public class SensorIntegrationAdminController {
    private final SensorIntegrationAdminService adminService;
    private final SensorIntegrationActivationService activationService;

    public SensorIntegrationAdminController(
            SensorIntegrationAdminService adminService,
            SensorIntegrationActivationService activationService
    ) {
        this.adminService = adminService;
        this.activationService = activationService;
    }

    @GetMapping("/profiles")
    public List<SensorIntegrationAdminDTOs.ProfileResponse> listProfiles(
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return adminService.listProfiles(includeInactive);
    }

    @GetMapping("/profiles/{profileId}")
    public SensorIntegrationAdminDTOs.ProfileResponse getProfile(@PathVariable UUID profileId) {
        return adminService.getProfile(profileId);
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public SensorIntegrationAdminDTOs.ProfileResponse createProfile(
            @RequestBody SensorIntegrationAdminDTOs.ProfileRequest request
    ) {
        return adminService.createProfile(request);
    }

    @PutMapping("/profiles/{profileId}")
    public SensorIntegrationAdminDTOs.ProfileResponse updateProfile(
            @PathVariable UUID profileId,
            @RequestBody SensorIntegrationAdminDTOs.ProfileUpdateRequest request
    ) {
        return adminService.updateProfile(profileId, request);
    }

    @PostMapping("/profiles/{profileId}/activate")
    public SensorIntegrationAdminDTOs.ProfileResponse activateProfile(@PathVariable UUID profileId) {
        return adminService.activateProfile(profileId);
    }

    @DeleteMapping("/profiles/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateProfile(@PathVariable UUID profileId) {
        adminService.deactivateProfile(profileId);
    }

    @PostMapping("/profiles/{profileId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public SensorIntegrationAdminDTOs.ParserVersionResponse createVersion(
            @PathVariable UUID profileId,
            @RequestBody SensorIntegrationAdminDTOs.ParserVersionRequest request
    ) {
        return adminService.createVersion(profileId, request);
    }

    @GetMapping("/profiles/{profileId}/versions")
    public List<SensorIntegrationAdminDTOs.ParserVersionResponse> listVersions(@PathVariable UUID profileId) {
        return adminService.listVersions(profileId);
    }

    @GetMapping("/profiles/{profileId}/versions/{versionId}")
    public SensorIntegrationAdminDTOs.ParserVersionResponse getVersion(
            @PathVariable UUID profileId,
            @PathVariable UUID versionId
    ) {
        return adminService.getVersion(profileId, versionId);
    }

    @PutMapping("/profiles/{profileId}/versions/{versionId}")
    public SensorIntegrationAdminDTOs.ParserVersionResponse updateVersion(
            @PathVariable UUID profileId,
            @PathVariable UUID versionId,
            @RequestBody SensorIntegrationAdminDTOs.ParserVersionRequest request
    ) {
        return adminService.updateVersion(profileId, versionId, request);
    }

    @PostMapping("/profiles/{profileId}/versions/{versionId}/activate")
    public SensorIntegrationAdminDTOs.ParserVersionResponse activateVersion(
            @PathVariable UUID profileId,
            @PathVariable UUID versionId,
            @RequestBody SensorIntegrationAdminDTOs.ActivationRequest request
    ) {
        return activationService.activateVersion(
                profileId,
                versionId,
                request != null ? request.expectedActiveVersionId() : null
        );
    }

    @PostMapping("/profiles/{profileId}/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public SensorIntegrationAdminDTOs.BindingResponse createBinding(
            @PathVariable UUID profileId,
            @RequestBody SensorIntegrationAdminDTOs.BindingRequest request
    ) {
        return adminService.createBinding(profileId, request);
    }

    @GetMapping("/profiles/{profileId}/bindings")
    public List<SensorIntegrationAdminDTOs.BindingResponse> listBindings(
            @PathVariable UUID profileId,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return adminService.listBindings(profileId, includeInactive);
    }

    @PostMapping("/bindings/{bindingId}/activate")
    public SensorIntegrationAdminDTOs.BindingResponse activateBinding(@PathVariable UUID bindingId) {
        return adminService.activateBinding(bindingId);
    }

    @DeleteMapping("/bindings/{bindingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateBinding(@PathVariable UUID bindingId) {
        adminService.deactivateBinding(bindingId);
    }
}
