package com.procel.api.controller.missions;

import com.procel.api.dto.missions.EventoDTOs;
import com.procel.api.service.missions.EventoDefinicaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(
        name = "Mission Events",
        description = "Catalogo persistente de definicoes de eventos de missoes e suas condicoes. Esta API apenas configura eventos; nao avalia medicoes nem cria atividades."
)
public class MissionEventsController {

    private final EventoDefinicaoService service;

    public MissionEventsController(EventoDefinicaoService service) {
        this.service = service;
    }

    @PostMapping("/api/missions/{missionId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cria definicao de evento para uma missao", description = "Requer ADMIN. Nao executa avaliacao nem cria atividades.")
    @ApiResponse(responseCode = "201", description = "Evento criado.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Missao nao encontrada.")
    @ApiResponse(responseCode = "409", description = "Tentativa de ativar evento de missao inativa.")
    public EventoDTOs.EventoDefinicaoResponse criarEvento(
            @Parameter(description = "ID da missao.") @PathVariable UUID missionId,
            @RequestBody EventoDTOs.EventoDefinicaoRequest req
    ) {
        return service.criarEvento(missionId, req);
    }

    @GetMapping("/api/missions/{missionId}/events")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista eventos configurados de uma missao", description = "Retorna eventos ordenados por ordem e criacao.")
    @ApiResponse(responseCode = "200", description = "Lista retornada.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Missao nao encontrada.")
    public List<EventoDTOs.EventoDefinicaoResponse> listarEventos(
            @Parameter(description = "ID da missao.") @PathVariable UUID missionId
    ) {
        return service.listarEventosDaMissao(missionId);
    }

    @GetMapping("/api/mission-events/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Busca definicao de evento")
    @ApiResponse(responseCode = "200", description = "Evento encontrado.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Evento nao encontrado.")
    public EventoDTOs.EventoDefinicaoResponse buscarEvento(
            @Parameter(description = "ID do evento.") @PathVariable UUID eventId
    ) {
        return service.buscarEvento(eventId);
    }

    @PutMapping("/api/mission-events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza definicao de evento", description = "Requer ADMIN. Evento de missao inativa pode ser editado, mas nao ativado.")
    @ApiResponse(responseCode = "200", description = "Evento atualizado.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Evento nao encontrado.")
    @ApiResponse(responseCode = "409", description = "Tentativa de ativar evento de missao inativa.")
    public EventoDTOs.EventoDefinicaoResponse atualizarEvento(
            @Parameter(description = "ID do evento.") @PathVariable UUID eventId,
            @RequestBody EventoDTOs.EventoDefinicaoRequest req
    ) {
        return service.atualizarEvento(eventId, req);
    }

    @DeleteMapping("/api/mission-events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desativa logicamente definicao de evento", description = "Marca ativo=false sem remover condicoes.")
    @ApiResponse(responseCode = "200", description = "Evento desativado.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Evento nao encontrado.")
    public void removerEvento(
            @Parameter(description = "ID do evento.") @PathVariable UUID eventId
    ) {
        service.removerEvento(eventId);
    }

    @PostMapping("/api/mission-events/{eventId}/conditions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cria condicao para evento", description = "Requer ADMIN. A condicao referencia ParametroDef por FK.")
    @ApiResponse(responseCode = "201", description = "Condicao criada.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos ou incompatíveis com DataType.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Evento ou parametro nao encontrado.")
    @ApiResponse(responseCode = "409", description = "Ordem duplicada no evento.")
    public EventoDTOs.EventoCondicaoResponse criarCondicao(
            @Parameter(description = "ID do evento.") @PathVariable UUID eventId,
            @RequestBody EventoDTOs.EventoCondicaoRequest req
    ) {
        return service.criarCondicao(eventId, req);
    }

    @PutMapping("/api/mission-events/{eventId}/conditions/{conditionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza condicao de evento")
    @ApiResponse(responseCode = "200", description = "Condicao atualizada.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos ou incompatíveis com DataType.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Evento, condicao ou parametro nao encontrado.")
    @ApiResponse(responseCode = "409", description = "Ordem duplicada no evento.")
    public EventoDTOs.EventoCondicaoResponse atualizarCondicao(
            @Parameter(description = "ID do evento.") @PathVariable UUID eventId,
            @Parameter(description = "ID da condicao.") @PathVariable UUID conditionId,
            @RequestBody EventoDTOs.EventoCondicaoRequest req
    ) {
        return service.atualizarCondicao(eventId, conditionId, req);
    }

    @DeleteMapping("/api/mission-events/{eventId}/conditions/{conditionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove condicao de evento")
    @ApiResponse(responseCode = "200", description = "Condicao removida.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Evento ou condicao nao encontrado.")
    public void removerCondicao(
            @Parameter(description = "ID do evento.") @PathVariable UUID eventId,
            @Parameter(description = "ID da condicao.") @PathVariable UUID conditionId
    ) {
        service.removerCondicao(eventId, conditionId);
    }
}
