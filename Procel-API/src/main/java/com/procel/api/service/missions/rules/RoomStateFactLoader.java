package com.procel.api.service.missions.rules;

import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.sensors.ParametroValor;
import com.procel.api.repository.sensors.AvaliacaoParametroValorRepository;
import com.procel.api.repository.sensors.ParametroValorRepository;
import com.procel.api.service.academic.AcademicContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RoomStateFactLoader {
    private static final ZoneId ACADEMIC_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int DEFAULT_MAX_GAP_SECONDS = 300;

    private final NamedParameterJdbcTemplate jdbc;
    private final ParametroValorRepository valores;
    private final AvaliacaoParametroValorRepository avaliacoes;
    private final MeasurementFactFactory factory;

    public RoomStateFactLoader(
            NamedParameterJdbcTemplate jdbc,
            ParametroValorRepository valores,
            AvaliacaoParametroValorRepository avaliacoes,
            MeasurementFactFactory factory
    ) {
        this.jdbc = jdbc;
        this.valores = valores;
        this.avaliacoes = avaliacoes;
        this.factory = factory;
    }

    public RoomStateFacts load(EventoDefinicao event, String roomId, Instant measuredAt, AcademicContext academic) {
        List<UUID> parameterIds = event.getCondicoes().stream()
                .filter(condition -> condition.isAtivo())
                .map(condition -> condition.getParametroDef().getId())
                .distinct()
                .toList();
        if (parameterIds.isEmpty()) return new RoomStateFacts(List.of(), List.of());

        int gap = event.getLacunaMaximaSegundos() == null
                ? DEFAULT_MAX_GAP_SECONDS : event.getLacunaMaximaSegundos();
        Instant from = measuredAt.minusSeconds(gap);
        if (academic != null && !academic.empty() && academic.inicio() != null) {
            Instant classStart = academic.inicio().atZone(ACADEMIC_ZONE).toInstant();
            if (classStart.isAfter(from)) from = classStart;
        }

        var params = new MapSqlParameterSource()
                .addValue("room", roomId)
                .addValue("ids", parameterIds)
                .addValue("from", Timestamp.from(from), Types.TIMESTAMP)
                .addValue("at", Timestamp.from(measuredAt), Types.TIMESTAMP);
        List<UUID> ids = jdbc.query("""
                select distinct on (pv.parametro_def_id) pv.id
                from parametro_valor pv
                join medicao m on m.id = pv.medicao_id
                join sensor s on s.external_id = m.sensor_external_id
                where s.compartimento_id = :room
                  and pv.parametro_def_id in (:ids)
                  and m.timestamp between :from and :at
                  and (pv.numeric_value is not null or pv.boolean_value is not null or pv.text_value is not null)
                order by pv.parametro_def_id, m.timestamp desc, m.id desc
                """, params, (rs, row) -> rs.getObject(1, UUID.class));

        Map<UUID, ParametroValor> byId = valores.findAllById(ids).stream()
                .collect(Collectors.toMap(ParametroValor::getId, Function.identity()));
        List<MeasurementFact> facts = ids.stream().map(byId::get)
                .filter(value -> value != null)
                .map(factory::from)
                .toList();
        var evaluations = avaliacoes.findAllByParametroValor_IdIn(ids).stream()
                .filter(evaluation -> evaluation.getRegraParametro() != null)
                .map(evaluation -> new RuleEvaluationFact(
                        evaluation.getId(),
                        evaluation.getRegraParametro().getId(),
                        evaluation.getParametroValor().getId(),
                        evaluation.getResultado()))
                .toList();
        return new RoomStateFacts(facts, evaluations);
    }

    public record RoomStateFacts(List<MeasurementFact> measurements, List<RuleEvaluationFact> ruleEvaluations) {}
}
