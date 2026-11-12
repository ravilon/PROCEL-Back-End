package com.proben.api.controller.people;

import com.proben.api.dto.people.PontosDTOs;
import com.proben.api.entity.missions.PontosLancamentoTipo;
import com.proben.api.service.missions.PontosQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST responsavel pelas operacoes de pessoa pontos.
 */
@RestController
@RequestMapping("/api/pessoas/{pessoaId}/pontos")
@Tag(name = "Pontos", description = "Consulta do ledger auditavel de pontos.")
public class PessoaPontosController {

    private final PontosQueryService pontosQueryService;

    public PessoaPontosController(PontosQueryService pontosQueryService) {
        this.pontosQueryService = pontosQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Consulta saldo de pontos", description = "ADMIN e OPERADOR podem consultar qualquer pessoa. USUARIO comum consulta apenas o proprio saldo.")
    @ApiResponse(responseCode = "200", description = "Saldo retornado.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para consultar outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public PontosDTOs.PontosSaldoResponse saldo(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId
    ) {
        return pontosQueryService.saldo(pessoaId);
    }

    @GetMapping("/lancamentos")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Consulta extrato de pontos", description = "Extrato paginado do ledger, ordenado por data de criacao desc e id desc.")
    @ApiResponse(responseCode = "200", description = "Extrato retornado.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para consultar outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public PontosDTOs.PontosLancamentosPageResponse lancamentos(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "Filtro opcional por tipo.", example = "CONCESSAO") @RequestParam(required = false) PontosLancamentoTipo tipo,
            @Parameter(description = "Pagina zero-based.", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da pagina.", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return pontosQueryService.lancamentos(pessoaId, tipo, page, size);
    }
}
