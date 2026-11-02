package com.procel.api.controller.shared;

import com.procel.api.dto.shared.SharedContextDTOs.*;
import com.procel.api.service.shared.SharedContextCollector;
import com.procel.api.service.shared.SharedContextService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shared-contexts")
public class SharedContextController {
    private final SharedContextService service;
    private final SharedContextCollector collector;
    public SharedContextController(SharedContextService service, SharedContextCollector collector){this.service=service;this.collector=collector;}

    @GetMapping @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public List<ContextResponse> list(){return service.list();}
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public ContextResponse get(@PathVariable UUID id){return service.get(id);}
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ContextResponse create(@RequestBody ContextRequest request){return service.create(request);}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ContextResponse update(@PathVariable UUID id,@RequestBody ContextRequest request){return service.update(id,request);}
    @PutMapping("/{id}/rooms") @PreAuthorize("hasRole('ADMIN')")
    public ContextResponse rooms(@PathVariable UUID id,@RequestBody RoomsRequest request){return service.replaceRooms(id,request);}
    @PutMapping("/rooms/{roomId}/source") @PreAuthorize("hasRole('ADMIN')")
    public void source(@PathVariable String roomId,@RequestBody SourceSelectionRequest request){service.selectSource(roomId,request);}
    @PutMapping("/rooms/source-batch") @PreAuthorize("hasRole('ADMIN')")
    public void sourceBatch(@RequestBody BatchSourceSelectionRequest request){service.selectSourceBatch(request);}
    @PutMapping("/{id}/integration") @PreAuthorize("hasRole('ADMIN')")
    public IntegrationResponse integration(@PathVariable UUID id,@RequestBody IntegrationRequest request){return service.configureIntegration(id,request);}
    @PostMapping("/{id}/integration/test") @PreAuthorize("hasRole('ADMIN')")
    public CollectionTestResponse testConfiguration(@PathVariable UUID id,@RequestBody IntegrationRequest request){return collector.testConfiguration(id,request);}
    @GetMapping("/{id}/integrations") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public List<IntegrationResponse> integrations(@PathVariable UUID id){return service.integrations(id);}
    @PostMapping("/integrations/{id}/collect") @PreAuthorize("hasRole('ADMIN')")
    public CollectionTestResponse collect(@PathVariable UUID id){return collector.collectNow(id);}
    @PostMapping("/integrations/{id}/test") @PreAuthorize("hasRole('ADMIN')")
    public CollectionTestResponse test(@PathVariable UUID id){return collector.testNow(id);}
    @PostMapping("/integrations/{id}/activate") @PreAuthorize("hasRole('ADMIN')")
    public IntegrationResponse activate(@PathVariable UUID id){return service.setIntegrationActive(id,true);}
    @PostMapping("/integrations/{id}/deactivate") @PreAuthorize("hasRole('ADMIN')")
    public IntegrationResponse deactivate(@PathVariable UUID id){return service.setIntegrationActive(id,false);}
    @DeleteMapping("/integrations/{id}/credential") @PreAuthorize("hasRole('ADMIN')")
    public IntegrationResponse removeCredential(@PathVariable UUID id){return service.removeManagedCredential(id);}
    @GetMapping("/rooms/{roomId}/parameters/{parameterId}/latest") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public ObservationResponse latest(@PathVariable String roomId,@PathVariable UUID parameterId,@RequestParam(required=false) Instant at){return service.latest(roomId,parameterId,at);}
    @GetMapping("/rooms/{roomId}/parameters/{parameterId}/average-7d") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public SevenDayAverageResponse average(@PathVariable String roomId,@PathVariable UUID parameterId,@RequestParam(required=false) Instant at){return service.average7d(roomId,parameterId,at);}
}
