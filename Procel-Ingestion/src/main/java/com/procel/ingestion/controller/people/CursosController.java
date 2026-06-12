package com.procel.ingestion.controller.people;

import com.procel.ingestion.dto.people.CursoDTOs;
import com.procel.ingestion.service.people.CursoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Cursos", description = "Gestao de cursos e vinculo principal da pessoa.")
public class CursosController {

    private final CursoService service;

    public CursosController(CursoService service) {
        this.service = service;
    }

    @GetMapping("/api/cursos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista cursos")
    public List<CursoDTOs.CursoResponse> listar(@RequestParam(required = false) String q) {
        return service.listar(q);
    }

    @GetMapping("/api/cursos/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Busca curso")
    public CursoDTOs.CursoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/api/cursos")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Cria curso")
    public CursoDTOs.CursoResponse criar(@RequestBody CursoDTOs.CursoRequest request) {
        return service.criar(request);
    }

    @PutMapping("/api/cursos/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Atualiza curso")
    public CursoDTOs.CursoResponse atualizar(
            @PathVariable Long id,
            @RequestBody CursoDTOs.CursoRequest request
    ) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/api/cursos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Remove curso sem pessoas vinculadas")
    public void remover(@PathVariable Long id) {
        service.remover(id);
    }

    @GetMapping("/api/pessoas/{pessoaId}/curso")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA') or #pessoaId == authentication.name")
    @Operation(summary = "Consulta o curso da pessoa")
    public CursoDTOs.PessoaCursoResponse cursoDaPessoa(@PathVariable String pessoaId) {
        return service.cursoDaPessoa(pessoaId);
    }

    @PutMapping("/api/pessoas/{pessoaId}/curso/{cursoId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Vincula ou substitui o curso da pessoa")
    public CursoDTOs.PessoaCursoResponse vincular(
            @PathVariable String pessoaId,
            @PathVariable Long cursoId
    ) {
        return service.vincular(pessoaId, cursoId);
    }

    @DeleteMapping("/api/pessoas/{pessoaId}/curso")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Remove o vinculo da pessoa com o curso")
    public CursoDTOs.PessoaCursoResponse removerVinculo(@PathVariable String pessoaId) {
        return service.removerVinculo(pessoaId);
    }
}
