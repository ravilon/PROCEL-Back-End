package com.procel.ingestion.controller;

import com.procel.ingestion.dto.people.PessoaDTOs;
import com.procel.ingestion.service.people.PessoaService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pessoas")
public class PessoasController {

    private final PessoaService service;

    public PessoasController(PessoaService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PessoaDTOs.PessoaResponse create(@RequestBody PessoaDTOs.CreatePessoaRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.name")
    public PessoaDTOs.PessoaResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.name")
    public PessoaDTOs.PessoaResponse update(@PathVariable String id, @RequestBody PessoaDTOs.UpdatePessoaRequest req, Authentication authentication) {
        PessoaDTOs.UpdatePessoaRequest effectiveReq = isAdmin(authentication)
                ? req
                : new PessoaDTOs.UpdatePessoaRequest(
                        req.nome(),
                        req.email(),
                        req.userId(),
                        req.password(),
                        req.telefone(),
                        req.matricula(),
                        null
                );
        return service.update(id, effectiveReq);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
