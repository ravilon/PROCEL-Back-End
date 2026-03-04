package com.procel.ingestion.controller;

import com.procel.ingestion.dto.people.PresencaDTOs;
import com.procel.ingestion.service.people.PresencaService;
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
    public PresencaDTOs.PresencaResponse checkin(@RequestBody PresencaDTOs.CheckinRequest req) {
        return service.checkin(req);
    }

    @PostMapping("/checkout")
    public PresencaDTOs.PresencaResponse checkout(@RequestBody PresencaDTOs.CheckoutRequest req) {
        return service.checkout(req);
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
    public PresencaDTOs.PresencaResponse checkoutByPessoa(@RequestBody PresencaDTOs.CheckoutByPessoaRequest req) {
        return service.checkoutByPessoa(req);
    }
}