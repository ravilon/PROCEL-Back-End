package com.procel.ingestion.controller.catalog;

import com.procel.ingestion.dto.catalog.CatalogoDTOs;
import com.procel.ingestion.service.catalog.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalogo", description = "Consultas de catalogo para navegacao administrativa.")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping("/compartimentos")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA','USUARIO')")
    @Operation(summary = "Lista compartimentos para busca e navegacao")
    public List<CatalogoDTOs.CompartimentoResponse> compartimentos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String predio,
            @RequestParam(required = false) String unidade,
            @RequestParam(required = false) String campus
    ) {
        return service.listarCompartimentos(q, tipo, predio, unidade, campus);
    }

    @GetMapping("/compartimentos/filter-options")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA','USUARIO')")
    @Operation(summary = "Lista opcoes disponiveis para os filtros de compartimentos")
    public CatalogoDTOs.CompartimentoFilterOptionsResponse opcoesFiltrosCompartimentos() {
        return service.opcoesFiltrosCompartimentos();
    }

    @GetMapping("/compartimentos/{id}/sensores")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista sensores diretamente vinculados ao compartimento")
    public List<CatalogoDTOs.SensorResponse> sensoresDoCompartimento(@PathVariable String id) {
        return service.listarSensoresDoCompartimento(id);
    }

    @GetMapping("/compartimentos/{id}/periodos-aula")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA','USUARIO')")
    @Operation(summary = "Lista os periodos de aula mais recentes do compartimento")
    public List<CatalogoDTOs.PeriodoAulaResponse> periodosDoCompartimento(@PathVariable String id) {
        return service.periodosDoCompartimento(id);
    }

    @GetMapping("/sensores")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista sensores para busca e navegacao")
    public List<CatalogoDTOs.SensorResponse> sensores(@RequestParam(required = false) String q) {
        return service.listarSensores(q);
    }

    @GetMapping("/tipos-sensor")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Lista tipos de sensor")
    public List<String> tiposSensor() {
        return service.listarTiposSensor();
    }

    @GetMapping("/disciplinas")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista disciplinas para busca e vinculo de alunos")
    public List<CatalogoDTOs.DisciplinaResponse> disciplinas(@RequestParam(required = false) String q) {
        return service.listarDisciplinas(q);
    }

    @GetMapping("/disciplinas/{id}/periodos-aula")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA','USUARIO')")
    @Operation(summary = "Lista os periodos de aula mais recentes da disciplina")
    public List<CatalogoDTOs.PeriodoAulaResponse> periodosDaDisciplina(@PathVariable Long id) {
        return service.periodosDaDisciplina(id);
    }

    @GetMapping("/pessoas")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Lista pessoas para busca e navegacao administrativa")
    public List<CatalogoDTOs.PessoaResumoResponse> pessoas(@RequestParam(required = false) String q) {
        return service.listarPessoas(q);
    }
}
