package com.procel.ingestion.controller;

import com.procel.ingestion.dto.people.PessoaDTOs;
import com.procel.ingestion.service.people.PessoaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pessoas")
public class PessoasController {

    private final PessoaService service;

    public PessoasController(PessoaService service) {
        this.service = service;
    }

    @PostMapping
    public PessoaDTOs.PessoaResponse create(@RequestBody PessoaDTOs.CreatePessoaRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public PessoaDTOs.PessoaResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public PessoaDTOs.PessoaResponse update(@PathVariable String id, @RequestBody PessoaDTOs.UpdatePessoaRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}