package com.procel.ingestion.controller;

import com.procel.ingestion.dto.people.PresencaDTOs;
import com.procel.ingestion.service.people.PresencaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/presencas")
@Tag(name = "Presencas", description = "Check-in, checkout e consultas de ocupacao.")
public class PresencasController {

    private final PresencaService service;

    public PresencasController(PresencaService service) {
        this.service = service;
    }

    @PostMapping("/checkin")
    @Operation(
            summary = "Registra check-in",
            description = "Requer ADMIN, OPERADOR ou USUARIO. ADMIN/OPERADOR podem informar pessoaId; USUARIO comum sempre usa o subject do JWT."
    )
    @ApiResponse(responseCode = "200", description = "Check-in registrado.")
    @ApiResponse(responseCode = "400", description = "Body ou compartimentoId ausente/invalido.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao para check-in.")
    @ApiResponse(responseCode = "404", description = "Pessoa ou compartimento nao encontrado.")
    @ApiResponse(responseCode = "409", description = "Pessoa ja possui presenca aberta.")
    public PresencaDTOs.PresencaResponse checkin(@RequestBody PresencaDTOs.CheckinRequest req, Authentication authentication) {
        String pessoaId = canActForOthers(authentication) && req != null && req.pessoaId() != null && !req.pessoaId().isBlank()
                ? req.pessoaId()
                : authentication.getName();
        return service.checkinForPessoa(pessoaId, req);
    }

    @PostMapping("/checkout")
    @Operation(
            summary = "Registra checkout por presenca",
            description = "Requer ADMIN, OPERADOR ou USUARIO. USUARIO comum so pode fechar a propria presenca."
    )
    @ApiResponse(responseCode = "200", description = "Checkout registrado.")
    @ApiResponse(responseCode = "400", description = "presencaId ausente ou checkoutAt anterior ao check-in.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao para checkout.")
    @ApiResponse(responseCode = "404", description = "Presenca nao encontrada ou nao pertence ao usuario comum.")
    @ApiResponse(responseCode = "409", description = "Presenca ja fechada.")
    public PresencaDTOs.PresencaResponse checkout(@RequestBody PresencaDTOs.CheckoutRequest req, Authentication authentication) {
        return service.checkout(req, authentication.getName(), canActForOthers(authentication));
    }

    @GetMapping("/abertas/compartimentos/{compartimentoId}")
    @Operation(summary = "Lista presencas abertas por compartimento", description = "Requer ADMIN ou OPERADOR.")
    @ApiResponse(responseCode = "200", description = "Lista retornada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    public List<PresencaDTOs.PresencaResponse> abertas(
            @Parameter(description = "ID do compartimento.", example = "2") @PathVariable String compartimentoId
    ) {
        return service.abertasPorCompartimento(compartimentoId);
    }

    @GetMapping("/ocupacao/compartimentos/{compartimentoId}")
    @Operation(summary = "Consulta ocupacao atual", description = "Requer ADMIN, OPERADOR, ANALISTA ou USUARIO.")
    @ApiResponse(responseCode = "200", description = "Ocupacao retornada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "404", description = "Compartimento nao encontrado.")
    public PresencaDTOs.OcupacaoResponse ocupacao(
            @Parameter(description = "ID do compartimento.", example = "2") @PathVariable String compartimentoId
    ) {
        return service.ocupacaoAtual(compartimentoId);
    }

    @PostMapping("/checkout/by-pessoa")
    @Operation(
            summary = "Registra checkout da presenca aberta por pessoa",
            description = "Requer ADMIN, OPERADOR ou USUARIO. ADMIN/OPERADOR podem informar pessoaId; USUARIO comum sempre usa o subject do JWT."
    )
    @ApiResponse(responseCode = "200", description = "Checkout registrado.")
    @ApiResponse(responseCode = "400", description = "checkoutAt anterior ao check-in.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Role sem permissao.")
    @ApiResponse(responseCode = "404", description = "Nenhuma presenca aberta encontrada.")
    public PresencaDTOs.PresencaResponse checkoutByPessoa(@RequestBody PresencaDTOs.CheckoutByPessoaRequest req, Authentication authentication) {
        String pessoaId = canActForOthers(authentication) && req != null && req.pessoaId() != null && !req.pessoaId().isBlank()
                ? req.pessoaId()
                : authentication.getName();
        return service.checkoutByPessoa(pessoaId, req == null ? null : req.checkoutAt());
    }

    private static boolean canActForOthers(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                                || authority.getAuthority().equals("ROLE_OPERADOR"));
    }
}
