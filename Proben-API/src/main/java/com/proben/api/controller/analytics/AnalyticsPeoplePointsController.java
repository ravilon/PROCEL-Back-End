package com.proben.api.controller.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consulta saldos agregados de pontos por pessoa para analytics. */
@RestController
@RequestMapping("/api/analytics/people-points")
@Tag(name = "Analytics", description = "Consultas agregadas de analytics.")
public class AnalyticsPeoplePointsController {
    private final JdbcTemplate jdbcTemplate;

    public AnalyticsPeoplePointsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','ANALISTA')")
    @Operation(summary = "Lista pessoas e seus saldos de pontos")
    public List<PersonPoints> list(@RequestParam(required = false) String q) {
        String term = q == null ? "" : q.trim();
        String pattern = "%" + term + "%";
        return jdbcTemplate.query("""
                select p.id, p.nome, p.email,
                       coalesce(sum(l.pontos), 0) as saldo_total,
                       coalesce(sum(case when l.tipo = 'CONCESSAO' then l.pontos else 0 end), 0) as total_concedido,
                       coalesce(sum(case when l.tipo = 'ESTORNO' then l.pontos else 0 end), 0) as total_estornado,
                       coalesce(sum(case when l.tipo = 'AJUSTE' then l.pontos else 0 end), 0) as total_ajustado
                from pessoa p left join pontos_lancamento l on l.pessoa_id = p.id
                where (? = '' or p.id ilike ? or p.nome ilike ? or p.email ilike ?)
                group by p.id, p.nome, p.email
                order by saldo_total desc, lower(p.nome), p.id
                limit 200
                """, (rs, rowNum) -> new PersonPoints(
                rs.getString("id"), rs.getString("nome"), rs.getString("email"),
                rs.getLong("saldo_total"), rs.getLong("total_concedido"),
                rs.getLong("total_estornado"), rs.getLong("total_ajustado")
        ), term, pattern, pattern, pattern);
    }

    public record PersonPoints(String pessoaId, String nome, String email, long saldoTotalPontos,
                               long totalConcedidoPontos, long totalEstornadoPontos, long totalAjustadoPontos) {}
}
