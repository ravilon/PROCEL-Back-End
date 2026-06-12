package com.procel.ingestion.controller;

import com.procel.ingestion.dto.people.AlunoDisciplinaDTOs;
import com.procel.ingestion.service.people.AlunoDisciplinaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pessoas/{pessoaId}/disciplinas")
@Tag(name = "Pessoas", description = "Cadastro e consulta de pessoas/usuarios da API.")
public class AlunoDisciplinasController {

    private final AlunoDisciplinaService service;

    public AlunoDisciplinasController(AlunoDisciplinaService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'OPERADOR') or " +
                    "#pessoaId == authentication.name"
    )
    @Operation(
            summary = "Vincula uma disciplina ao aluno",
            description = "ADMIN/OPERADOR podem vincular para qualquer pessoa. " +
                    "USUARIO pode vincular apenas as proprias disciplinas."
    )
    @ApiResponse(responseCode = "200", description = "Vinculo criado.")
    @ApiResponse(responseCode = "400", description = "Dados invalidos.")
    @ApiResponse(responseCode = "404", description = "Pessoa ou disciplina nao encontrada.")
    @ApiResponse(responseCode = "409", description = "Vinculo ja existente.")
    public AlunoDisciplinaDTOs.DisciplinaAlunoResponse vincular(
            @Parameter(description = "ID da pessoa.", example = "ravilon")
            @PathVariable
            String pessoaId,
            @RequestBody
            AlunoDisciplinaDTOs.VincularDisciplinaRequest req
    ) {
        return service.vincular(pessoaId, req);
    }

    @GetMapping
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'OPERADOR', 'ANALISTA') or " +
                    "#pessoaId == authentication.name"
    )
    @Operation(summary = "Lista as disciplinas do aluno em um periodo letivo")
    @ApiResponse(responseCode = "200", description = "Disciplinas retornadas.")
    @ApiResponse(responseCode = "400", description = "Periodo letivo invalido.")
    @ApiResponse(responseCode = "404", description = "Pessoa nao encontrada.")
    public List<AlunoDisciplinaDTOs.DisciplinaAlunoResponse> listar(
            @Parameter(description = "ID da pessoa.", example = "ravilon")
            @PathVariable
            String pessoaId,
            @Parameter(description = "Periodo letivo no formato AAAA/S.", example = "2026/1")
            @RequestParam
            String periodoLetivo
    ) {
        return service.listarPorPeriodo(pessoaId, periodoLetivo);
    }

    @PutMapping("/{vinculoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Atualiza o status do vinculo com a disciplina")
    @ApiResponse(responseCode = "200", description = "Vinculo atualizado.")
    @ApiResponse(responseCode = "404", description = "Vinculo nao encontrado.")
    public AlunoDisciplinaDTOs.DisciplinaAlunoResponse atualizar(
            @PathVariable
            String pessoaId,
            @PathVariable
            UUID vinculoId,
            @RequestBody
            AlunoDisciplinaDTOs.AtualizarVinculoRequest req
    ) {
        return service.atualizar(pessoaId, vinculoId, req);
    }

    @DeleteMapping("/{vinculoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Remove o vinculo do aluno com a disciplina")
    @ApiResponse(responseCode = "204", description = "Vinculo removido.")
    @ApiResponse(responseCode = "404", description = "Vinculo nao encontrado.")
    public void remover(
            @PathVariable
            String pessoaId,
            @PathVariable
            UUID vinculoId
    ) {
        service.remover(pessoaId, vinculoId);
    }
}
