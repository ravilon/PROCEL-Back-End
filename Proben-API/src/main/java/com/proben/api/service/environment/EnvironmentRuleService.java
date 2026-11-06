package com.proben.api.service.environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proben.api.dto.environment.EnvironmentRuleDTOs.*;
import com.proben.api.exception.ConflictException;
import com.proben.api.exception.NotFoundException;
import com.proben.api.service.academic.AcademicContext;
import com.proben.api.service.academic.AcademicContextResolver;
import com.proben.api.service.shared.SharedContextService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EnvironmentRuleService {
    public static final String TEN_MINUTES_PRESET = "0 */10 * * * *";
    private static final Set<String> SCOPES = Set.of("GLOBAL", "CAMPUS", "ROOM");
    private static final Set<String> OCCUPANCY = Set.of("IGNORE", "REQUIRE_OCCUPIED", "REQUIRE_SCHEDULED_USE");
    private static final Set<String> BENEFICIARIES = Set.of("NONE", "SCHEDULED_STUDENTS", "CONFIRMED_PRESENCE");
    private static final Set<String> OPERATORS = Set.of("GT", "GTE", "LT", "LTE", "EQ", "NEQ", "BETWEEN", "OUTSIDE");
    private static final Set<String> SOURCES = Set.of("LOCAL", "SHARED");
    private static final Pattern CATEGORY = Pattern.compile("^[A-Z][A-Z0-9_]{1,79}$");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AcademicContextResolver academics;
    private final SharedContextService shared;

    public EnvironmentRuleService(JdbcTemplate jdbc, ObjectMapper json,
                                  AcademicContextResolver academics, SharedContextService shared) {
        this.jdbc = jdbc;
        this.json = json;
        this.academics = academics;
        this.shared = shared;
    }

    public List<RuleResponse> list(String campus, String room, String category, String scope, Boolean active) {
        return jdbc.query("select id from regra_ambiente where (?::varchar is null or campus_nome=?) and (?::varchar is null or compartimento_id=?) " +
                        "and (?::varchar is null or categoria=?) and (?::varchar is null or escopo=?) and (?::boolean is null or ativo=?) order by categoria,nome",
                (rs, n) -> get(rs.getObject(1, UUID.class)), campus, campus, room, room,
                upper(category), upper(category), upper(scope), upper(scope), active, active);
    }

    public RuleResponse get(UUID id) {
        var rows = jdbc.query("select * from regra_ambiente where id=?", this::rule, id);
        if (rows.isEmpty()) throw new NotFoundException("Regra de ambiente not found id=" + id);
        return rows.getFirst();
    }

    @Transactional
    public RuleResponse create(RuleRequest request) {
        Normalized r = validate(request);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                    insert into regra_ambiente(id,nome,descricao,categoria,ativo,padrao,escopo,campus_nome,compartimento_id,
                      timezone,cron_expression,dias_semana,vigencia_inicio,vigencia_fim,politica_ocupacao,politica_beneficiario,
                      monitorar_sem_pontos,pontuacao,configuracao,created_by,updated_by)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)
                    """);
                bind(ps, id, r, actor());
                return ps;
            });
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Já existe uma regra ativa no mesmo escopo, sala/campus e categoria");
        }
        return get(id);
    }

    @Transactional
    public RuleResponse update(UUID id, RuleRequest request) {
        get(id);
        Normalized r = validate(request);
        try {
            int changed = jdbc.update(con -> {
                var ps = con.prepareStatement("""
                    update regra_ambiente set nome=?,descricao=?,categoria=?,ativo=?,padrao=?,escopo=?,campus_nome=?,compartimento_id=?,
                      timezone=?,cron_expression=?,dias_semana=?,vigencia_inicio=?,vigencia_fim=?,politica_ocupacao=?,politica_beneficiario=?,
                      monitorar_sem_pontos=?,pontuacao=?::jsonb,configuracao=?::jsonb,versao=versao+1,updated_at=now(),updated_by=? where id=?
                    """);
                bindUpdate(ps, id, r, actor());
                return ps;
            });
            if (changed != 1) throw new NotFoundException("Regra de ambiente not found id=" + id);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Já existe uma regra ativa no mesmo escopo, sala/campus e categoria");
        }
        return get(id);
    }

    @Transactional
    public RuleResponse setActive(UUID id, boolean active) {
        RuleResponse current = get(id);
        RuleRequest request = new RuleRequest(current.nome(), current.descricao(), current.categoria(), active,
                current.padrao(), current.escopo(), current.campusNome(), current.compartimentoId(), current.timezone(),
                current.cronExpression(), current.diasSemana(), current.vigenciaInicio(), current.vigenciaFim(),
                current.politicaOcupacao(), current.politicaBeneficiario(), current.monitorarSemPontos(),
                current.pontuacao(), current.configuracao());
        return update(id, request);
    }

    @Transactional
    public RuleResponse duplicate(UUID id) {
        RuleResponse r = get(id);
        return create(new RuleRequest(r.nome() + " (cópia)", r.descricao(), r.categoria(), false,
                r.padrao(), r.escopo(), r.campusNome(), r.compartimentoId(), r.timezone(), r.cronExpression(),
                r.diasSemana(), r.vigenciaInicio(), r.vigenciaFim(), r.politicaOcupacao(), r.politicaBeneficiario(),
                r.monitorarSemPontos(), r.pontuacao(), r.configuracao()));
    }

    @Transactional
    public List<RuleResponse> copyToRooms(UUID id, CopyRequest request) {
        RuleResponse r = get(id);
        if (!"ROOM".equals(r.escopo())) throw new IllegalArgumentException("Only ROOM rules can be copied");
        if (request.compartimentoIds().isEmpty()) throw new IllegalArgumentException("compartimentoIds is required");
        List<RuleResponse> copies = new ArrayList<>();
        for (String room : request.compartimentoIds().stream().map(String::trim).distinct().toList()) {
            copies.add(create(new RuleRequest(r.nome(), r.descricao(), r.categoria(), r.ativo(), false, "ROOM", null,
                    room, r.timezone(), r.cronExpression(), r.diasSemana(), r.vigenciaInicio(), r.vigenciaFim(),
                    r.politicaOcupacao(), r.politicaBeneficiario(), r.monitorarSemPontos(), r.pontuacao(), r.configuracao())));
        }
        return copies;
    }

    public ResolutionResponse resolve(String roomId, String category) {
        requireRoom(roomId);
        String normalizedCategory = category(category);
        List<RuleResponse> specific = candidates("select r.* from regra_ambiente r where r.ativo and r.escopo='ROOM' and r.compartimento_id=? and r.categoria=?", roomId, normalizedCategory);
        List<RuleResponse> campus = candidates("""
            select r.* from regra_ambiente r join compartimento c on c.id=? join predio p on p.id=c.predio_id
            where r.ativo and r.escopo='CAMPUS' and r.campus_nome=p.campus_id and r.categoria=?
            """, roomId, normalizedCategory);
        List<RuleResponse> global = candidates("select r.* from regra_ambiente r where r.ativo and r.escopo='GLOBAL' and r.categoria=?", normalizedCategory);
        ensureUnambiguous(specific, campus, global);
        RuleResponse effective = !specific.isEmpty() ? specific.getFirst() : !campus.isEmpty() ? campus.getFirst() : global.isEmpty() ? null : global.getFirst();
        String origin = effective == null ? null : !specific.isEmpty() ? "SPECIFIC" : !campus.isEmpty() ? "CAMPUS_DEFAULT" : "GLOBAL_DEFAULT";
        return new ResolutionResponse(roomId, normalizedCategory, first(specific), first(campus), first(global), effective, origin);
    }

    public List<ResolutionResponse> resolveAll(String roomId) {
        return jdbc.query("select distinct categoria from regra_ambiente where ativo order by categoria",
                (rs, n) -> resolve(roomId, rs.getString(1)));
    }

    @Transactional
    public ExecutionResponse manual(UUID ruleId, String roomId, Instant at) {
        RuleResponse rule = get(ruleId);
        String effectiveRoom = "ROOM".equals(rule.escopo()) ? rule.compartimentoId() : requireRoom(roomId);
        ResolutionResponse resolution = resolve(effectiveRoom, rule.categoria());
        if (resolution.effective() == null || !resolution.effective().id().equals(ruleId))
            throw new ConflictException("Rule is not effective for room and category");
        Instant scheduled = at == null ? Instant.now() : at;
        UUID id = enqueue(rule, effectiveRoom, resolution.origem(), "MANUAL", scheduled, UUID.randomUUID().toString(), actor());
        return execution(id);
    }

    public TestResponse test(UUID ruleId, TestRequest request) {
        RuleResponse rule = get(ruleId);
        Instant at = request.instante() == null ? Instant.now() : request.instante();
        Evaluation e = evaluate(rule, request.compartimentoId(), at, false, null);
        return new TestResponse(e.result, e.reason, e.evidence, e.academic, e.occupancy, e.beneficiaries, e.points);
    }

    public List<ExecutionResponse> executions(UUID ruleId, String roomId, String result, int limit, int offset) {
        return jdbc.query("select * from regra_ambiente_execucao where (?::uuid is null or regra_id=?) and (?::varchar is null or compartimento_id=?) " +
                        "and (?::varchar is null or resultado=?) order by created_at desc limit ? offset ?", (rs, row) -> execution(rs, row),
                ruleId, ruleId, roomId, roomId, upper(result), upper(result), Math.min(Math.max(limit, 1), 200), Math.max(offset, 0));
    }

    public ExecutionResponse execution(UUID id) {
        var rows = jdbc.query("select * from regra_ambiente_execucao where id=?", (rs, row) -> execution(rs, row), id);
        if (rows.isEmpty()) throw new NotFoundException("Execução de regra de ambiente not found id=" + id);
        return rows.getFirst();
    }

    public DeletePreview deletePreview(UUID id) {
        get(id);
        Long count = jdbc.queryForObject("select count(*) from regra_ambiente_execucao where regra_id=?", Long.class, id);
        long n = count == null ? 0 : count;
        return new DeletePreview(id, n, n == 0, n == 0 ? "DELETE" : "DEACTIVATE");
    }

    public List<Instant> nextRuns(UUID id, int count, Instant after) {
        RuleResponse rule = get(id);
        int limit = Math.min(Math.max(count, 1), 20);
        ZonedDateTime cursor = (after == null ? Instant.now() : after).atZone(ZoneId.of(rule.timezone()));
        CronExpression cron = CronExpression.parse(rule.cronExpression());
        List<Instant> result = new ArrayList<>();
        while (result.size() < limit) {
            cursor = cron.next(cursor);
            if (cursor == null) break;
            Instant candidate = cursor.toInstant();
            if (rule.diasSemana().contains(cursor.getDayOfWeek().getValue())
                    && (rule.vigenciaInicio() == null || !candidate.isBefore(rule.vigenciaInicio()))
                    && (rule.vigenciaFim() == null || candidate.isBefore(rule.vigenciaFim()))) result.add(candidate);
            if (rule.vigenciaFim() != null && !candidate.isBefore(rule.vigenciaFim())) break;
        }
        return result;
    }

    @Transactional
    public void delete(UUID id) {
        DeletePreview preview = deletePreview(id);
        if (!preview.deletable()) throw new ConflictException("Regra possui histórico; desative-a em vez de excluir");
        jdbc.update("delete from regra_ambiente where id=?", id);
    }

    UUID enqueue(RuleResponse rule, String room, String origin, String type, Instant scheduled, String suffix, String createdBy) {
        String key = rule.id() + ":" + rule.versao() + ":" + room + ":" + rule.categoria() + ":" + scheduled + ":" + suffix;
        UUID id = UUID.randomUUID();
        String snapshot = write(json.valueToTree(rule));
        var ids = jdbc.query("""
            insert into regra_ambiente_execucao(id,regra_id,regra_versao,regra_snapshot,compartimento_id,categoria,origem,
              tipo_execucao,instante_previsto,timezone,idempotency_key,created_by)
            values (?,?,?,?::jsonb,?,?,?,?,?,?,?,?) on conflict(idempotency_key) do nothing returning id
            """, (rs, n) -> rs.getObject(1, UUID.class), id, rule.id(), rule.versao(), snapshot, room, rule.categoria(),
                origin, type, Timestamp.from(scheduled), rule.timezone(), key, createdBy);
        if (!ids.isEmpty()) return ids.getFirst();
        return jdbc.queryForObject("select id from regra_ambiente_execucao where idempotency_key=?", UUID.class, key);
    }

    Evaluation evaluate(RuleResponse rule, String roomId, Instant at, boolean persistPoints, UUID executionId) {
        if (at.isAfter(Instant.now().plusSeconds(5))) return skipped("SKIPPED_FUTURE_DATA", "scheduled instant is in the future");
        AcademicContext academic = academics.resolve(roomId, at);
        ObjectNode academicJson = json.valueToTree(academic);
        OccupancyState occupancy = occupancy(rule, roomId, at, academic);
        ObjectNode occupancyJson = json.createObjectNode().put("occupied", occupancy.occupied).put("source", occupancy.source);
        if ("REQUIRE_SCHEDULED_USE".equals(rule.politicaOcupacao()) && academic.empty())
            return skipped("SKIPPED_NO_SCHEDULED_USE", "no scheduled academic use", academicJson, occupancyJson);
        if ("REQUIRE_OCCUPIED".equals(rule.politicaOcupacao()) && !occupancy.occupied)
            return skipped("SKIPPED_NO_OCCUPANCY", "room is not occupied", academicJson, occupancyJson);

        BeneficiaryState beneficiary = beneficiaries(rule, roomId, at, academic);
        if (!"NONE".equals(rule.politicaBeneficiario()) && beneficiary.ids.isEmpty())
            return skipped("SKIPPED_NO_BENEFICIARY", beneficiary.reason, academicJson, occupancyJson);

        ObjectNode evidence = json.createObjectNode();
        boolean satisfied = true;
        for (JsonNode condition : rule.configuracao().path("conditions")) {
            String alias = requiredText(condition, "alias");
            UUID parameter = UUID.fromString(requiredText(condition, "parametroDefId"));
            String source = condition.path("source").asText("LOCAL").toUpperCase(Locale.ROOT);
            Observation observation = "SHARED".equals(source)
                    ? sharedObservation(roomId, parameter, at, condition.path("sevenDayAverage").asBoolean(false))
                    : localObservation(roomId, parameter, at, condition);
            evidence.set(alias, observation.json);
            if (observation.result != null)
                return skipped(observation.result, observation.reason, academicJson, occupancyJson, evidence, beneficiary.json);
            if (condition.has("value") && !matches(observation.value, condition)) satisfied = false;
        }

        if ("ADAPTIVE_COMFORT".equals(rule.configuracao().path("preset").asText())) {
            JsonNode ti = evidence.path("Ti"); JsonNode temed = evidence.path("Temed");
            if (!ti.hasNonNull("value") || !temed.hasNonNull("value"))
                return skipped("SKIPPED_NO_DATA", "Ti and Temed are required", academicJson, occupancyJson, evidence, beneficiary.json);
            var band = AdaptiveComfortCalculator.calculate(temed.get("value").decimalValue(),
                    decimal(rule.configuracao(), "lowerOffset", "3.5"), decimal(rule.configuracao(), "upperOffset", "3.5"));
            evidence.set("comfortBand", json.valueToTree(band));
            BigDecimal internal = ti.get("value").decimalValue();
            satisfied = satisfied && internal.compareTo(band.lower()) >= 0 && internal.compareTo(band.upper()) <= 0;
        }
        int points = rule.monitorarSemPontos() ? 0 : rule.pontuacao().path(satisfied ? "satisfied" : "notSatisfied").asInt(0);
        return new Evaluation(satisfied ? "SATISFIED" : "NOT_SATISFIED", null, evidence, academicJson,
                occupancyJson, beneficiary.json, Math.max(points, 0), beneficiary.ids);
    }

    private Observation localObservation(String room, UUID parameter, Instant at, JsonNode config) {
        String sensor = text(config, "sensorExternalId");
        int maxAge = config.path("maxAgeSeconds").asInt(600);
        ObjectNode base = json.createObjectNode().put("parameterId", parameter.toString())
                .put("source", "LOCAL").put("maxAgeSeconds", maxAge);
        if (sensor != null) base.put("sensorExternalId", sensor);
        var rows = jdbc.query("""
            select m.id,pv.id,s.external_id,m.timestamp,pv.numeric_value,pv.boolean_value,pv.text_value,
              p.id parameter_id,p.nome parameter_name,p.numeric_unit,p.data_type
            from sensor s join medicao m on m.sensor_external_id=s.external_id
            join parametro_valor pv on pv.medicao_id=m.id and pv.parametro_def_id=?
            join parametro_def p on p.id=pv.parametro_def_id and p.tipo_nome=s.tipo_nome and p.ativo
            where s.ativo and s.compartimento_id=? and (?::varchar is null or s.external_id=?) and m.timestamp<=?
            order by m.timestamp desc,m.id desc limit 1
            """, (rs, n) -> observationRow(rs, at, maxAge), parameter, room, sensor, sensor, Timestamp.from(at));
        if (!rows.isEmpty()) return rows.getFirst();
        base.put("rejectionReason", "SKIPPED_NO_DATA").put("rejectionDetail", "required local measurement is missing");
        return Observation.skip("SKIPPED_NO_DATA", "required local measurement is missing", base, json);
    }

    private Observation sharedObservation(String room, UUID parameter, Instant at, boolean average) {
        if (average) {
            var avg = shared.average7d(room, parameter, at);
            ObjectNode node = json.valueToTree(avg);
            node.put("origin", "SHARED");
            if (!"AVAILABLE".equals(avg.status())) return rejected("SKIPPED_INSUFFICIENT_COVERAGE", "external average has insufficient coverage", node);
            node.set("value", json.valueToTree(avg.average()));
            return new Observation(avg.average(), null, null, node);
        }
        var latest = shared.latest(room, parameter, at);
        ObjectNode node = json.valueToTree(latest);
        node.put("origin", "SHARED");
        if ("MISSING".equals(latest.status())) return rejected("SKIPPED_NO_DATA", "selected shared source has no measurement", node);
        if ("STALE".equals(latest.status())) return rejected("SKIPPED_STALE_DATA", "selected shared measurement is stale", node);
        node.set("value", json.valueToTree(latest.value()));
        return new Observation(latest.value(), null, null, node);
    }

    private Observation observationRow(ResultSet rs, Instant at, int maxAge) throws SQLException {
        Instant observed = rs.getTimestamp("timestamp").toInstant();
        ObjectNode node = json.createObjectNode().put("measurementId", rs.getObject(1).toString())
                .put("parameterValueId", rs.getObject(2).toString()).put("sensorExternalId", rs.getString(3))
                .put("observedAt", observed.toString()).put("parameterId", rs.getObject("parameter_id").toString())
                .put("parameterName", rs.getString("parameter_name")).put("dataType", rs.getString("data_type"))
                .put("source", "LOCAL").put("maxAgeSeconds", maxAge);
        if (rs.getString("numeric_unit") != null) node.put("unit", rs.getString("numeric_unit"));
        if (observed.isAfter(at)) return rejected("SKIPPED_FUTURE_DATA", "observation is in the future", node);
        if (observed.isBefore(at.minusSeconds(maxAge))) return rejected("SKIPPED_STALE_DATA", "measurement is stale", node);
        Object raw = rs.getBigDecimal(5) != null ? rs.getBigDecimal(5) : rs.getObject(6) != null ? rs.getBoolean(6) : rs.getString(7);
        if (raw == null) return rejected("SKIPPED_NO_DATA", "measurement value is null", node);
        node.set("value", json.valueToTree(raw));
        return new Observation(raw, null, null, node);
    }

    private Observation rejected(String result, String reason, ObjectNode node) {
        node.put("rejectionReason", result).put("rejectionDetail", reason);
        return Observation.skip(result, reason, node, json);
    }

    private OccupancyState occupancy(RuleResponse rule, String room, Instant at, AcademicContext academic) {
        if (!academic.empty()) return new OccupancyState(true, "SCHEDULED_USE");
        JsonNode parameter = rule.configuracao().get("occupancyParametroDefId");
        if (parameter == null || parameter.isNull()) return new OccupancyState(false, "NO_SENSOR_CONFIGURED");
        Observation observation = localObservation(room, UUID.fromString(parameter.asText()), at,
                json.createObjectNode().put("maxAgeSeconds", rule.configuracao().path("occupancyMaxAgeSeconds").asInt(600)));
        if (observation.result != null) return new OccupancyState(false, observation.result);
        boolean occupied = observation.value instanceof Boolean b ? b : new BigDecimal(observation.value.toString()).compareTo(BigDecimal.ZERO) > 0;
        return new OccupancyState(occupied, "SENSOR");
    }

    private BeneficiaryState beneficiaries(RuleResponse rule, String room, Instant at, AcademicContext academic) {
        if ("NONE".equals(rule.politicaBeneficiario())) return new BeneficiaryState(List.of(), json.createArrayNode(), "monitoring only");
        if (academic.empty()) return new BeneficiaryState(List.of(), json.createArrayNode(), "no scheduled academic use");
        List<String> eligible = academic.pessoaElegivelIds();
        if ("SCHEDULED_STUDENTS".equals(rule.politicaBeneficiario()))
            return beneficiaryState(eligible, "students linked to scheduled class; presence not implied");
        List<String> confirmed = eligible.stream().filter(person -> Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from presenca where pessoa_id=? and compartimento_id=? and checkin_at<=?
              and (checkout_at is null or checkout_at>?))
            """, Boolean.class, person, room, Timestamp.from(at), Timestamp.from(at)))).toList();
        return beneficiaryState(confirmed, confirmed.isEmpty() ? "no explicit presence evidence" : "confirmed by check-in");
    }

    private BeneficiaryState beneficiaryState(List<String> ids, String reason) {
        ArrayNode array = json.createArrayNode(); ids.forEach(array::add);
        return new BeneficiaryState(ids, array, reason);
    }

    private boolean matches(Object value, JsonNode c) {
        String op = c.path("operator").asText("EQ").toUpperCase(Locale.ROOT);
        if (value instanceof Boolean b) return switch (op) { case "EQ" -> b == c.path("value").asBoolean(); case "NEQ" -> b != c.path("value").asBoolean(); default -> throw new IllegalArgumentException("boolean condition supports EQ/NEQ"); };
        if (!(value instanceof Number) && !isNumber(value.toString())) return switch (op) { case "EQ" -> value.toString().equals(c.path("value").asText()); case "NEQ" -> !value.toString().equals(c.path("value").asText()); default -> throw new IllegalArgumentException("text condition supports EQ/NEQ"); };
        BigDecimal actual = new BigDecimal(value.toString()); BigDecimal first = c.path("value").decimalValue();
        return switch (op) { case "GT" -> actual.compareTo(first)>0; case "GTE" -> actual.compareTo(first)>=0; case "LT" -> actual.compareTo(first)<0; case "LTE" -> actual.compareTo(first)<=0; case "EQ" -> actual.compareTo(first)==0; case "NEQ" -> actual.compareTo(first)!=0; case "BETWEEN" -> actual.compareTo(first)>=0 && actual.compareTo(c.path("value2").decimalValue())<=0; case "OUTSIDE" -> actual.compareTo(first)<0 || actual.compareTo(c.path("value2").decimalValue())>0; default -> throw new IllegalArgumentException("invalid operator"); };
    }

    private Normalized validate(RuleRequest r) {
        if (r == null) throw new IllegalArgumentException("request is required");
        String category = category(r.categoria()); String scope = enumValue(r.escopo(), SCOPES, "escopo");
        boolean standard = Boolean.TRUE.equals(r.padrao()); String campus = blank(r.campusNome()); String room = blank(r.compartimentoId());
        if ("ROOM".equals(scope) && (standard || room == null || campus != null)) throw new IllegalArgumentException("ROOM rule must be non-default, have room and no campus");
        if ("CAMPUS".equals(scope) && (!standard || campus == null || room != null)) throw new IllegalArgumentException("CAMPUS default must have campus and no room");
        if ("GLOBAL".equals(scope) && (!standard || campus != null || room != null)) throw new IllegalArgumentException("GLOBAL default cannot have campus or room");
        if (room != null) requireRoom(room);
        if (campus != null && !exists("select exists(select 1 from campus where nome=?)", campus)) throw new NotFoundException("Campus not found nome=" + campus);
        String zone = blank(r.timezone()) == null ? "America/Sao_Paulo" : r.timezone().trim(); ZoneId.of(zone);
        String cron = blank(r.cronExpression()) == null ? TEN_MINUTES_PRESET : r.cronExpression().trim(); CronExpression.parse(cron);
        List<Integer> days = r.diasSemana() == null || r.diasSemana().isEmpty() ? List.of(1,2,3,4,5,6,7) : r.diasSemana().stream().distinct().sorted().toList();
        if (days.stream().anyMatch(d -> d < 1 || d > 7)) throw new IllegalArgumentException("diasSemana must contain ISO values 1..7");
        if (r.vigenciaInicio()!=null && r.vigenciaFim()!=null && !r.vigenciaFim().isAfter(r.vigenciaInicio())) throw new IllegalArgumentException("vigenciaFim must be after vigenciaInicio");
        validateConfiguration(r.configuracao()); validatePoints(r.pontuacao());
        return new Normalized(r.nome().trim(), blank(r.descricao()), category, r.ativo()==null || r.ativo(), standard, scope, campus, room, zone, cron, days,
                r.vigenciaInicio(), r.vigenciaFim(), enumValue(r.politicaOcupacao(), OCCUPANCY, "politicaOcupacao"),
                enumValue(r.politicaBeneficiario(), BENEFICIARIES, "politicaBeneficiario"), Boolean.TRUE.equals(r.monitorarSemPontos()), r.pontuacao(), r.configuracao());
    }

    private void validateConfiguration(JsonNode config) {
        if (config == null || !config.isObject()) throw new IllegalArgumentException("configuracao must be an object");
        JsonNode conditions = config.path("conditions"); if (!conditions.isArray()) throw new IllegalArgumentException("configuracao.conditions must be an array");
        Set<String> aliases = new HashSet<>();
        for (JsonNode c : conditions) {
            String alias = requiredText(c,"alias"); if (!aliases.add(alias)) throw new IllegalArgumentException("condition aliases must be unique");
            UUID parameter = UUID.fromString(requiredText(c,"parametroDefId"));
            String source = enumValue(c.path("source").asText("LOCAL"), SOURCES, "source");
            String op = c.path("operator").asText("EQ").toUpperCase(Locale.ROOT); if (!OPERATORS.contains(op)) throw new IllegalArgumentException("invalid condition operator");
            if (!exists("select exists(select 1 from parametro_def where id=? and ativo)", parameter))
                throw new IllegalArgumentException("condition parameter is inactive or missing");
            String sensor = text(c, "sensorExternalId");
            if ("LOCAL".equals(source) && sensor != null && !exists("""
                    select exists(select 1 from sensor s join parametro_def p on p.id=? and p.tipo_nome=s.tipo_nome
                      where s.external_id=? and s.ativo and p.ativo)
                    """, parameter, sensor))
                throw new IllegalArgumentException("condition parameter is incompatible with sensor");
            if (!c.has("value") && !c.path("sevenDayAverage").asBoolean(false)
                    && !"ADAPTIVE_COMFORT".equals(config.path("preset").asText()))
                throw new IllegalArgumentException("condition value is required");
            if ((op.equals("BETWEEN") || op.equals("OUTSIDE")) && !c.has("value2")) throw new IllegalArgumentException("value2 is required");
        }
    }

    private void validatePoints(JsonNode points) {
        if (points == null || !points.isObject()) throw new IllegalArgumentException("pontuacao must be an object");
        for (String key : List.of("satisfied","notSatisfied")) if (points.has(key) && (!points.get(key).canConvertToInt() || points.get(key).asInt()<0)) throw new IllegalArgumentException("points must be non-negative integers");
    }

    private List<RuleResponse> candidates(String sql, Object... args) { return jdbc.query(sql, this::rule, args); }
    private void ensureUnambiguous(List<RuleResponse>... levels) { for (var level : levels) if (level.size()>1) throw new ConflictException("Ambiguous active environment rule resolution"); }
    private RuleResponse first(List<RuleResponse> values) { return values.isEmpty()?null:values.getFirst(); }
    private String requireRoom(String room) { if (blank(room)==null) throw new IllegalArgumentException("compartimentoId is required"); if(!exists("select exists(select 1 from compartimento where id=?)",room.trim()))throw new NotFoundException("Compartimento not found id="+room);return room.trim(); }
    private boolean exists(String sql,Object...args){return Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,args));}
    private String actor(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null||a.getName()==null?"SYSTEM":a.getName();}
    private String category(String value){String v=upper(value);if(v==null||!CATEGORY.matcher(v).matches())throw new IllegalArgumentException("invalid categoria");return v;}
    private String enumValue(String value,Set<String> allowed,String field){String v=upper(value);if(v==null||!allowed.contains(v))throw new IllegalArgumentException("invalid "+field);return v;}
    private static String upper(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT);}
    private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String text(JsonNode n,String f){return n.hasNonNull(f)&&!n.get(f).asText().isBlank()?n.get(f).asText().trim():null;}
    private static String requiredText(JsonNode n,String f){String v=text(n,f);if(v==null)throw new IllegalArgumentException(f+" is required");return v;}
    private static boolean isNumber(String v){try{new BigDecimal(v);return true;}catch(NumberFormatException ex){return false;}}
    private static BigDecimal decimal(JsonNode n,String f,String d){return n.has(f)?n.get(f).decimalValue():new BigDecimal(d);}
    private String write(JsonNode node){try{return json.writeValueAsString(node);}catch(Exception ex){throw new IllegalArgumentException("invalid JSON",ex);}}
    private JsonNode read(String value){try{return value==null?null:json.readTree(value);}catch(Exception ex){throw new IllegalStateException("invalid stored JSON",ex);}}
    private Instant instant(Timestamp value){return value==null?null:value.toInstant();}

    private RuleResponse rule(ResultSet rs,int row) throws SQLException {
        Array daysArray=rs.getArray("dias_semana"); Object[] raw=daysArray==null?new Object[0]:(Object[])daysArray.getArray();
        List<Integer> days=Arrays.stream(raw).map(value->((Number)value).intValue()).toList();
        return new RuleResponse(rs.getObject("id",UUID.class),rs.getString("nome"),rs.getString("descricao"),rs.getString("categoria"),rs.getBoolean("ativo"),rs.getBoolean("padrao"),rs.getString("escopo"),rs.getString("campus_nome"),rs.getString("compartimento_id"),rs.getString("timezone"),rs.getString("cron_expression"),days,instant(rs.getTimestamp("vigencia_inicio")),instant(rs.getTimestamp("vigencia_fim")),rs.getString("politica_ocupacao"),rs.getString("politica_beneficiario"),rs.getBoolean("monitorar_sem_pontos"),read(rs.getString("pontuacao")),read(rs.getString("configuracao")),rs.getInt("versao"),instant(rs.getTimestamp("created_at")),rs.getString("created_by"),instant(rs.getTimestamp("updated_at")),rs.getString("updated_by"));
    }

    private ExecutionResponse execution(ResultSet rs,int row)throws SQLException{return new ExecutionResponse(rs.getObject("id",UUID.class),rs.getObject("regra_id",UUID.class),rs.getInt("regra_versao"),rs.getString("compartimento_id"),rs.getString("categoria"),rs.getString("origem"),rs.getString("tipo_execucao"),instant(rs.getTimestamp("instante_previsto")),instant(rs.getTimestamp("instante_real")),rs.getString("timezone"),rs.getString("status"),rs.getString("resultado"),rs.getString("motivo"),read(rs.getString("evidencias")),read(rs.getString("contexto_academico")),read(rs.getString("ocupacao")),read(rs.getString("beneficiarios")),rs.getInt("pontos_calculados"),read(rs.getString("lancamentos")),rs.getInt("attempts"),instant(rs.getTimestamp("lease_until")),(Long)rs.getObject("duration_ms"),instant(rs.getTimestamp("created_at")));}

    private void bind(java.sql.PreparedStatement ps,UUID id,Normalized r,String actor)throws SQLException{ps.setObject(1,id);ps.setString(2,r.name);ps.setString(3,r.description);ps.setString(4,r.category);ps.setBoolean(5,r.active);ps.setBoolean(6,r.standard);ps.setString(7,r.scope);ps.setString(8,r.campus);ps.setString(9,r.room);ps.setString(10,r.zone);ps.setString(11,r.cron);ps.setArray(12,ps.getConnection().createArrayOf("smallint",r.days.toArray()));ps.setTimestamp(13,r.start==null?null:Timestamp.from(r.start));ps.setTimestamp(14,r.end==null?null:Timestamp.from(r.end));ps.setString(15,r.occupancy);ps.setString(16,r.beneficiary);ps.setBoolean(17,r.monitor);ps.setString(18,write(r.points));ps.setString(19,write(r.config));ps.setString(20,actor);ps.setString(21,actor);}
    private void bindUpdate(java.sql.PreparedStatement ps,UUID id,Normalized r,String actor)throws SQLException{ps.setString(1,r.name);ps.setString(2,r.description);ps.setString(3,r.category);ps.setBoolean(4,r.active);ps.setBoolean(5,r.standard);ps.setString(6,r.scope);ps.setString(7,r.campus);ps.setString(8,r.room);ps.setString(9,r.zone);ps.setString(10,r.cron);ps.setArray(11,ps.getConnection().createArrayOf("smallint",r.days.toArray()));ps.setTimestamp(12,r.start==null?null:Timestamp.from(r.start));ps.setTimestamp(13,r.end==null?null:Timestamp.from(r.end));ps.setString(14,r.occupancy);ps.setString(15,r.beneficiary);ps.setBoolean(16,r.monitor);ps.setString(17,write(r.points));ps.setString(18,write(r.config));ps.setString(19,actor);ps.setObject(20,id);}

    private Evaluation skipped(String status,String reason){return skipped(status,reason,json.createObjectNode(),json.createObjectNode());}
    private Evaluation skipped(String status,String reason,JsonNode academic,JsonNode occupancy){return skipped(status,reason,academic,occupancy,json.createObjectNode(),json.createArrayNode());}
    private Evaluation skipped(String status,String reason,JsonNode academic,JsonNode occupancy,JsonNode evidence,JsonNode beneficiaries){return new Evaluation(status,reason,evidence,academic,occupancy,beneficiaries,0,List.of());}
    record Evaluation(String result,String reason,JsonNode evidence,JsonNode academic,JsonNode occupancy,JsonNode beneficiaries,int points,List<String> beneficiaryIds){}
    private record Observation(Object value,String result,String reason,ObjectNode json){static Observation skip(String r,String why,ObjectNode node,ObjectMapper json){return new Observation(null,r,why,node);}static Observation skip(String r,String why,ObjectNode node, Object ignored){return new Observation(null,r,why,node);}}
    private record OccupancyState(boolean occupied,String source){}
    private record BeneficiaryState(List<String> ids,ArrayNode json,String reason){}
    private record Normalized(String name,String description,String category,boolean active,boolean standard,String scope,String campus,String room,String zone,String cron,List<Integer> days,Instant start,Instant end,String occupancy,String beneficiary,boolean monitor,JsonNode points,JsonNode config){}
}
