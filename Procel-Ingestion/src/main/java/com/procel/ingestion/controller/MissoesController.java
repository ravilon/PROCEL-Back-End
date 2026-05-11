package com.procel.ingestion.controller;

import com.procel.ingestion.dto.missions.MissaoDTOs;
import com.procel.ingestion.entity.missions.MissaoStatus;
import com.procel.ingestion.service.missions.MissaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pessoas/{pessoaId}/missoes")
@Tag(name = "Missoes", description = "Cadastro e acompanhamento de missoes vinculadas a pessoas.")
public class MissoesController {

    private final MissaoService service;

    public MissoesController(MissaoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Cria missao para uma pessoa", description = "ADMIN/OPERADOR podem criar para qualquer pessoa. USUARIO cria apenas para si.")
    @ApiResponse(responseCode = "200", description = "Missao criada.")
    @ApiResponse(responseCode = "400", description = "Dados obrigatorios ausentes ou invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para criar missao para outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public MissaoDTOs.MissaoResponse create(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @RequestBody MissaoDTOs.CreateMissaoRequest req
    ) {
        return service.create(pessoaId, req);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Lista missoes de uma pessoa", description = "Permite filtrar por status.")
    @ApiResponse(responseCode = "200", description = "Lista retornada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para consultar missoes de outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public List<MissaoDTOs.MissaoResponse> list(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "Filtro opcional por status.", example = "PENDENTE") @RequestParam(required = false) MissaoStatus status
    ) {
        return service.list(pessoaId, status);
    }

    @GetMapping("/{missaoId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Busca missao de uma pessoa")
    @ApiResponse(responseCode = "200", description = "Missao encontrada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para consultar missao de outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Missao nao encontrada.")
    public MissaoDTOs.MissaoResponse get(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "ID da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID missaoId
    ) {
        return service.get(pessoaId, missaoId);
    }

    @PutMapping("/{missaoId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Atualiza missao de uma pessoa")
    @ApiResponse(responseCode = "200", description = "Missao atualizada.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para atualizar missao de outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Missao nao encontrada.")
    public MissaoDTOs.MissaoResponse update(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "ID da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID missaoId,
            @RequestBody MissaoDTOs.UpdateMissaoRequest req
    ) {
        return service.update(pessoaId, missaoId, req);
    }

    @DeleteMapping("/{missaoId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Remove missao de uma pessoa")
    @ApiResponse(responseCode = "200", description = "Missao removida.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para remover missao de outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Missao nao encontrada.")
    public void delete(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "ID da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID missaoId
    ) {
        service.delete(pessoaId, missaoId);
    }
}
