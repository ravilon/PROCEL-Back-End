package com.procel.ingestion.controller;

import com.procel.ingestion.dto.people.PresencaDTOs;
import com.procel.ingestion.service.people.PresencaService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/presencas")
public class PresencasController {

    private final PresencaService service;

    public PresencasController(PresencaService service) {
        this.service = service;
    }

    @PostMapping("/checkin")
    public PresencaDTOs.PresencaResponse checkin(@RequestBody PresencaDTOs.CheckinRequest req, Authentication authentication) {
        String pessoaId = canActForOthers(authentication) && req != null && req.pessoaId() != null && !req.pessoaId().isBlank()
                ? req.pessoaId()
                : authentication.getName();
        return service.checkinForPessoa(pessoaId, req);
    }

    @PostMapping("/checkout")
    public PresencaDTOs.PresencaResponse checkout(@RequestBody PresencaDTOs.CheckoutRequest req, Authentication authentication) {
        return service.checkout(req, authentication.getName(), canActForOthers(authentication));
    }

    @GetMapping("/abertas/compartimentos/{compartimentoId}")
    public List<PresencaDTOs.PresencaResponse> abertas(@PathVariable String compartimentoId) {
        return service.abertasPorCompartimento(compartimentoId);
    }

    @GetMapping("/ocupacao/compartimentos/{compartimentoId}")
    public PresencaDTOs.OcupacaoResponse ocupacao(@PathVariable String compartimentoId) {
        return service.ocupacaoAtual(compartimentoId);
    }

    @PostMapping("/checkout/by-pessoa")
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
