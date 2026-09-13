package com.procel.api.controller.people;

import com.procel.api.dto.people.XpDTOs;
import com.procel.api.entity.missions.XpLancamentoTipo;
import com.procel.api.service.missions.XpQueryService;
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

@RestController
@RequestMapping("/api/pessoas/{pessoaId}/xp")
@Tag(name = "XP", description = "Consulta do ledger auditavel de XP.")
public class PessoaXpController {

    private final XpQueryService xpQueryService;

    public PessoaXpController(XpQueryService xpQueryService) {
        this.xpQueryService = xpQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Consulta saldo de XP", description = "ADMIN e OPERADOR podem consultar qualquer pessoa. USUARIO comum consulta apenas o proprio saldo.")
    @ApiResponse(responseCode = "200", description = "Saldo retornado.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para consultar outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public XpDTOs.XpSaldoResponse saldo(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId
    ) {
        return xpQueryService.saldo(pessoaId);
    }

    @GetMapping("/lancamentos")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR') or #pessoaId == authentication.name")
    @Operation(summary = "Consulta extrato de XP", description = "Extrato paginado do ledger, ordenado por data de criacao desc e id desc.")
    @ApiResponse(responseCode = "200", description = "Extrato retornado.")
    @ApiResponse(responseCode = "403", description = "Sem permissao para consultar outra pessoa.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public XpDTOs.XpLancamentosPageResponse lancamentos(
            @Parameter(description = "ID da pessoa.", example = "ravilon") @PathVariable String pessoaId,
            @Parameter(description = "Filtro opcional por tipo.", example = "CONCESSAO") @RequestParam(required = false) XpLancamentoTipo tipo,
            @Parameter(description = "Pagina zero-based.", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da pagina.", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return xpQueryService.lancamentos(pessoaId, tipo, page, size);
    }
}
