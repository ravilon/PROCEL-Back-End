package com.proben.api.controller.environment;

import com.proben.api.dto.environment.EnvironmentRuleDTOs.*;
import com.proben.api.service.environment.EnvironmentRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Controlador REST responsavel pelas operacoes de environment rule.
 */
@RestController
@RequestMapping("/api/environment-rules")
public class EnvironmentRuleController {
    private final EnvironmentRuleService service;
    public EnvironmentRuleController(EnvironmentRuleService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public List<RuleResponse> list(@RequestParam(required=false) String campus,
                                   @RequestParam(required=false) String room,
                                   @RequestParam(required=false) String category,
                                   @RequestParam(required=false) String scope,
                                   @RequestParam(required=false) Boolean active) {
        return service.list(campus, room, category, scope, active);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public RuleResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public RuleResponse create(@Valid @RequestBody RuleRequest request) { return service.create(request); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public RuleResponse update(@PathVariable UUID id, @Valid @RequestBody RuleRequest request) { return service.update(id, request); }
    @PostMapping("/{id}/activate") @PreAuthorize("hasRole('ADMIN')")
    public RuleResponse activate(@PathVariable UUID id) { return service.setActive(id, true); }
    @PostMapping("/{id}/deactivate") @PreAuthorize("hasRole('ADMIN')")
    public RuleResponse deactivate(@PathVariable UUID id) { return service.setActive(id, false); }
    @PostMapping("/{id}/duplicate") @PreAuthorize("hasRole('ADMIN')")
    public RuleResponse duplicate(@PathVariable UUID id) { return service.duplicate(id); }
    @PostMapping("/{id}/copy") @PreAuthorize("hasRole('ADMIN')")
    public List<RuleResponse> copy(@PathVariable UUID id, @Valid @RequestBody CopyRequest request) { return service.copyToRooms(id, request); }
    @GetMapping("/resolution/{roomId}") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public List<ResolutionResponse> resolve(@PathVariable String roomId) { return service.resolveAll(roomId); }
    @GetMapping("/resolution/{roomId}/{category}") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public ResolutionResponse resolve(@PathVariable String roomId, @PathVariable String category) { return service.resolve(roomId, category); }
    @PostMapping("/{id}/test") @PreAuthorize("hasRole('ADMIN')")
    public TestResponse test(@PathVariable UUID id, @Valid @RequestBody TestRequest request) { return service.test(id, request); }
    @PostMapping("/{id}/execute") @PreAuthorize("hasRole('ADMIN')")
    public ExecutionResponse execute(@PathVariable UUID id, @RequestParam String roomId,
                                     @RequestParam(required=false) Instant instant) { return service.manual(id, roomId, instant); }
    @GetMapping("/executions") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public List<ExecutionResponse> executions(@RequestParam(required=false) UUID ruleId,
                                              @RequestParam(required=false) String roomId,
                                              @RequestParam(required=false) String result,
                                              @RequestParam(defaultValue="50") int limit,
                                              @RequestParam(defaultValue="0") int offset) {
        return service.executions(ruleId, roomId, result, limit, offset);
    }
    @GetMapping("/executions/{id}") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public ExecutionResponse execution(@PathVariable UUID id) { return service.execution(id); }
    @GetMapping("/{id}/delete-preview") @PreAuthorize("hasRole('ADMIN')")
    public DeletePreview deletePreview(@PathVariable UUID id) { return service.deletePreview(id); }
    @GetMapping("/{id}/next-runs") @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    public List<Instant> nextRuns(@PathVariable UUID id,
                                  @RequestParam(defaultValue="5") int count,
                                  @RequestParam(required=false) Instant after) {
        return service.nextRuns(id, count, after);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
