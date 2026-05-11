package com.procel.ingestion.controller;

import com.procel.ingestion.dto.missions.MissaoDTOs;
import com.procel.ingestion.entity.missions.AtividadeStatus;
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
@Tag(name = "Missoes", description = "Catalogo de missoes e atividades pessoa-missao.")
public class MissoesController {

    private final MissaoService service;

    public MissoesController(MissaoService service) {
        this.service = service;
    }

    @PostMapping("/api/missoes")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Cria modelo de missao", description = "Cria uma missao de catalogo. A missao so vira atividade quando atribuida a uma pessoa.")
    @ApiResponse(responseCode = "200", description = "Missao criada.")
    @ApiResponse(responseCode = "400", description = "Dados obrigatorios ausentes ou invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    public MissaoDTOs.MissaoResponse createMissao(@RequestBody MissaoDTOs.CreateMissaoRequest req) {
        return service.createMissao(req);
    }

    @GetMapping("/api/missoes")
    @Operation(summary = "Lista modelos de missao", description = "Permite filtrar por ativo.")
    @ApiResponse(responseCode = "200", description = "Lista retornada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    public List<MissaoDTOs.MissaoResponse> listMissoes(
            @Parameter(description = "Filtro opcional por ativo.", example = "true") @RequestParam(required = false) Boolean ativo
    ) {
        return service.listMissoes(ativo);
    }

    @GetMapping("/api/missoes/{missaoId}")
    @Operation(summary = "Busca modelo de missao")
    @ApiResponse(responseCode = "200", description = "Missao encontrada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "404", description = "Missao nao encontrada.")
    public MissaoDTOs.MissaoResponse getMissao(
            @Parameter(description = "ID da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID missaoId
    ) {
        return service.getMissao(missaoId);
    }

    @PutMapping("/api/missoes/{missaoId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Atualiza modelo de missao")
    @ApiResponse(responseCode = "200", description = "Missao atualizada.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Missao nao encontrada.")
    public MissaoDTOs.MissaoResponse updateMissao(
            @Parameter(description = "ID da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID missaoId,
            @RequestBody MissaoDTOs.UpdateMissaoRequest req
    ) {
        return service.updateMissao(missaoId, req);
    }

    @DeleteMapping("/api/missoes/{missaoId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Remove modelo de missao")
    @ApiResponse(responseCode = "200", description = "Missao removida.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao.")
    @ApiResponse(responseCode = "404", description = "Missao nao encontrada.")
    public void deleteMissao(
            @Parameter(description = "ID da missao.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID missaoId
    ) {
        service.deleteMissao(missaoId);
    }

    @PostMapping("/api/pessoas/{pessoaId}/atividades")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Atribui missao a uma pessoa", description = "Cria a atividade pessoa-missao. Completar uma missao atualiza esta relacao, nao o modelo de missao.")
    @ApiResponse(responseCode = "200", description = "Atividade criada.")
    @ApiResponse(responseCode = "400", description = "Dados obrigatorios ausentes ou invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para atribuir atividade a outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa ou missao nao encontrada.")
    @ApiResponse(responseCode = "409", description = "Missao inativa ou ja atribuida a pessoa.")
    public MissaoDTOs.AtividadeResponse atribuir(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @RequestBody MissaoDTOs.AtribuirMissaoRequest req
    ) {
        return service.atribuir(pessoaId, req);
    }

    @GetMapping("/api/pessoas/{pessoaId}/atividades")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Lista atividades de uma pessoa", description = "Lista relacoes pessoa-missao, com filtro opcional por status.")
    @ApiResponse(responseCode = "200", description = "Lista retornada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para consultar atividades de outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public List<MissaoDTOs.AtividadeResponse> listAtividades(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "Filtro opcional por status.", example = "PENDENTE") @RequestParam(required = false) AtividadeStatus status
    ) {
        return service.listAtividades(pessoaId, status);
    }

    @GetMapping("/api/pessoas/{pessoaId}/atividades/{atividadeId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Busca atividade de uma pessoa")
    @ApiResponse(responseCode = "200", description = "Atividade encontrada.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para consultar atividade de outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Atividade nao encontrada.")
    public MissaoDTOs.AtividadeResponse getAtividade(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "ID da atividade.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID atividadeId
    ) {
        return service.getAtividade(pessoaId, atividadeId);
    }

    @PutMapping("/api/pessoas/{pessoaId}/atividades/{atividadeId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Atualiza atividade de uma pessoa", description = "Use este endpoint para iniciar, concluir ou cancelar uma missao atribuida.")
    @ApiResponse(responseCode = "200", description = "Atividade atualizada.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para atualizar atividade de outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Atividade nao encontrada.")
    public MissaoDTOs.AtividadeResponse updateAtividade(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "ID da atividade.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID atividadeId,
            @RequestBody MissaoDTOs.UpdateAtividadeRequest req
    ) {
        return service.updateAtividade(pessoaId, atividadeId, req);
    }

    @DeleteMapping("/api/pessoas/{pessoaId}/atividades/{atividadeId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Remove atividade de uma pessoa")
    @ApiResponse(responseCode = "200", description = "Atividade removida.")
    @ApiResponse(responseCode = "401", description = "Token ausente ou invalido.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para remover atividade de outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Atividade nao encontrada.")
    public void deleteAtividade(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "ID da atividade.", example = "7655bd02-1302-4589-9902-8aa89ccf01c6") @PathVariable UUID atividadeId
    ) {
        service.deleteAtividade(pessoaId, atividadeId);
    }
}
