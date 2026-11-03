package com.proben.api.service.missions.rules;

import com.proben.api.entity.missions.EventoDefinicao;
import com.proben.api.entity.sensors.ParametroValor;
import com.proben.api.repository.sensors.AvaliacaoParametroValorRepository;
import com.proben.api.repository.sensors.ParametroValorRepository;
import com.proben.api.service.academic.AcademicContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
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
                select distinct on (candidate.parametro_def_id) candidate.valor_id
                from (
                    select pv.id as valor_id, pv.parametro_def_id, m.timestamp, m.id, 1 as priority
                    from parametro_valor pv
                    join medicao m on m.id = pv.medicao_id
                    join sensor s on s.external_id = m.sensor_external_id
                    where s.compartimento_id = :room
                      and s.ativo
                      and pv.parametro_def_id in (:ids)
                      and m.timestamp between :from and :at
                      and (pv.numeric_value is not null or pv.boolean_value is not null or pv.text_value is not null)
                    union all
                    select pv.id, pv.parametro_def_id, m.timestamp, m.id, 0 as priority
                    from contexto_fonte_selecao fs
                    join sensor s on s.external_id = fs.sensor_external_id and s.ativo
                    join contexto_compartilhado c on c.id = s.contexto_compartilhado_id and c.ativo
                    join contexto_compartilhado_compartimento cc
                      on cc.contexto_id = c.id and cc.compartimento_id = fs.compartimento_id and cc.ativo
                    join medicao m on m.sensor_external_id = s.external_id
                    join parametro_valor pv on pv.medicao_id = m.id and pv.parametro_def_id = fs.parametro_def_id
                    join contexto_integracao_externa i on i.sensor_external_id = s.external_id
                    join contexto_coleta_execucao ce on ce.integracao_id = i.id and ce.medicao_id = m.id
                    left join campus cp on cp.nome = c.campus_nome
                    where fs.compartimento_id = :room
                      and fs.parametro_def_id in (:ids)
                      and m.timestamp <= cast(:at as timestamptz)
                      and m.timestamp >= (cast(:at as timestamptz) - make_interval(secs => fs.validade_segundos))
                      and ce.location_hash = encode(digest(concat_ws('|',
                        case when c.location_mode='CAMPUS' then cp.latitude else c.latitude end,
                        case when c.location_mode='CAMPUS' then cp.longitude else c.longitude end), 'sha256'), 'hex')
                      and (pv.numeric_value is not null or pv.boolean_value is not null or pv.text_value is not null)
                ) candidate
                order by candidate.parametro_def_id, candidate.priority, candidate.timestamp desc, candidate.id desc
                """, params, (rs, row) -> rs.getObject(1, UUID.class));

        Map<UUID, ParametroValor> byId = valores.findAllById(ids).stream()
                .filter(value -> value != null)
                .collect(Collectors.toMap(
                        value -> value.getId(),
                        Function.identity()));
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
