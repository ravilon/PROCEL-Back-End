package com.procel.ingestion.controller;

import com.procel.ingestion.dto.people.PessoaDTOs;
import com.procel.ingestion.service.people.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pessoas")
@Tag(name = "Pessoas", description = "Cadastro e consulta de pessoas/usuarios da API.")
public class PessoasController {

    private final PessoaService service;

    public PessoasController(PessoaService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cria pessoa", description = "Requer role ADMIN. Se roles for omitido, a pessoa criada recebe USUARIO.")
    @ApiResponse(responseCode = "200", description = "Pessoa criada.")
    @ApiResponse(responseCode = "400", description = "Dados obrigatorios ausentes ou invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Token sem role ADMIN.")
    @ApiResponse(responseCode = "409", description = "userId, email ou matricula ja cadastrado.")
    public PessoaDTOs.PessoaResponse create(@RequestBody PessoaDTOs.CreatePessoaRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.name")
    @Operation(summary = "Busca pessoa por ID", description = "ADMIN pode buscar qualquer pessoa. USUARIO pode buscar apenas o proprio id.")
    @ApiResponse(responseCode = "200", description = "Pessoa encontrada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para acessar outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public PessoaDTOs.PessoaResponse get(@Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.name")
    @Operation(summary = "Atualiza pessoa", description = "ADMIN pode atualizar qualquer pessoa e roles. USUARIO pode atualizar apenas os proprios dados; roles informadas sao ignoradas.")
    @ApiResponse(responseCode = "200", description = "Pessoa atualizada.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para atualizar outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    @ApiResponse(responseCode = "409", description = "Conflito de email, matricula ou tentativa de alterar userId.")
    public PessoaDTOs.PessoaResponse update(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String id,
            @RequestBody PessoaDTOs.UpdatePessoaRequest req,
            Authentication authentication
    ) {
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
    @Operation(summary = "Remove pessoa", description = "Requer role ADMIN.")
    @ApiResponse(responseCode = "200", description = "Pessoa removida.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Token sem role ADMIN.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public void delete(@Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String id) {
        service.delete(id);
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
