package com.procel.api.service.missions.rules.drools;

import com.procel.api.entity.missions.EventoCondicao;
import com.procel.api.entity.missions.EventoDefinicao;
import com.procel.api.entity.missions.EventoModoAvaliacao;
import com.procel.api.entity.missions.EventoOperadorLogico;
import com.procel.api.entity.missions.EventoPoliticaAtribuicao;
import com.procel.api.entity.missions.EventoTipoDisparo;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.RegraOperador;
import com.procel.api.entity.sensors.TipoDeSensor;
import com.procel.api.service.missions.rules.ConditionEvaluationResult;
import com.procel.api.service.missions.rules.MeasurementFact;
import com.procel.api.service.missions.rules.MissionEvaluationContext;
import com.procel.api.service.missions.rules.MissionRuleEvaluationResult;
import com.procel.api.service.missions.rules.SimpleMissionRuleEngine;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DroolsMissionRuleEngineTest {

    private final DroolsMissionRuleEngine engine = new DroolsMissionRuleEngine(100, 900);
    private final SimpleMissionRuleEngine simple = new SimpleMissionRuleEngine();
    private final Instant now = Instant.parse("2026-09-13T12:00:00Z");

    @Test
    void instantAllIsSemanticallyEquivalentToSimple() {
        ParametroDef temperature = parameter("ac_setpoint_c", DataType.NUMERIC);
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoModoAvaliacao.INSTANTANEO, EventoOperadorLogico.ALL, null, null,
                numericCondition(temperature, RegraOperador.BETWEEN, "23", "25", true, true, 1),
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 2));
        MissionEvaluationContext context = context(event,
                numericFact(temperature, "24", now.minusSeconds(10), "sensor-1", "room-1"),
                booleanFact(presence, true, now.minusSeconds(10), "sensor-1", "room-1"));

        assertEquivalent(engine.evaluate(context), simple.evaluate(context));
    }

    @Test
    void instantAnyIsSemanticallyEquivalentToSimple() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        ParametroDef state = parameter("state", DataType.TEXT);
        EventoDefinicao event = event(EventoModoAvaliacao.INSTANTANEO, EventoOperadorLogico.ANY, null, null,
                numericCondition(temperature, RegraOperador.GT, "30", null, true, true, 1),
                textCondition(state, RegraOperador.CONTAINS, "ALERT", true, true, 2));
        MissionEvaluationContext context = context(event,
                numericFact(temperature, "24", now.minusSeconds(10), "sensor-1", "room-1"),
                textFact(state, "AC_ALERT_ON", now.minusSeconds(10), "sensor-1", "room-1"));

        assertEquivalent(engine.evaluate(context), simple.evaluate(context));
    }

    @Test
    void supportsNumericBooleanTextOptionalMissingAndNullLikeSimple() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        ParametroDef status = parameter("status", DataType.TEXT);
        EventoDefinicao event = event(EventoModoAvaliacao.INSTANTANEO, EventoOperadorLogico.ALL, null, null,
                numericCondition(temperature, RegraOperador.OUTSIDE, "15", "30", true, true, 1),
                booleanCondition(presence, RegraOperador.NEQ, false, true, true, 2),
                textCondition(status, RegraOperador.EQ, "OK", false, true, 3));

        MissionEvaluationContext matched = context(event,
                numericFact(temperature, "31", now.minusSeconds(10), "sensor-1", "room-1"),
                booleanFact(presence, true, now.minusSeconds(10), "sensor-1", "room-1"),
                textFact(status, "BROKEN", now.minusSeconds(10), "sensor-1", "room-1"));

        MissionRuleEvaluationResult result = engine.evaluate(matched);
        assertEquivalent(result, simple.evaluate(matched));
        assertThat(result.conditionResults()).extracting(ConditionEvaluationResult::matched)
                .containsExactly(true, true, false);

        MissionEvaluationContext missing = context(event,
                numericFact(temperature, "31", now.minusSeconds(10), "sensor-1", "room-1"));
        assertEquivalent(engine.evaluate(missing), simple.evaluate(missing));

        MissionEvaluationContext nullValue = context(event,
                fact(temperature, null, null, null, now.minusSeconds(10), "sensor-1", "room-1"),
                booleanFact(presence, true, now.minusSeconds(10), "sensor-1", "room-1"));
        assertEquivalent(engine.evaluate(nullValue), simple.evaluate(nullValue));
    }

    @Test
    void evidencePointsToTheSameParameterValuesAsSimple() {
        ParametroDef status = parameter("status", DataType.TEXT);
        EventoDefinicao event = event(EventoModoAvaliacao.INSTANTANEO, EventoOperadorLogico.ALL, null, null,
                textCondition(status, RegraOperador.CONTAINS, "sala \"fria\"", true, true, 1));
        MeasurementFact fact = textFact(status, "alerta: sala \"fria\"", now.minusSeconds(5), "sensor-1", "room-1");

        MissionRuleEvaluationResult result = engine.evaluate(context(event, fact));

        assertThat(result.matched()).isTrue();
        assertThat(result.evidences()).extracting(MeasurementFact::parametroValorId)
                .containsExactly(fact.parametroValorId());
        assertThat(result.conditionResults().getFirst().parametroValorId()).contains(fact.parametroValorId());
    }

    @Test
    void acInteligenteDurationRequiresThirtyContinuousMinutes() {
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        ParametroDef acStatus = parameter("ac_status", DataType.BOOLEAN);
        ParametroDef setpoint = parameter("ac_setpoint_c", DataType.NUMERIC);
        EventoDefinicao event = event(EventoModoAvaliacao.DURACAO, EventoOperadorLogico.ALL, 3_600, 1_800,
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1),
                booleanCondition(acStatus, RegraOperador.EQ, true, true, true, 2),
                numericCondition(setpoint, RegraOperador.BETWEEN, "23", "25", true, true, 3));

        MissionRuleEvaluationResult result = engine.evaluate(context(event,
                sample(now.minusSeconds(1_800), presence, true, acStatus, true, setpoint, "24"),
                sample(now.minusSeconds(1_200), presence, true, acStatus, true, setpoint, "24"),
                sample(now.minusSeconds(600), presence, true, acStatus, true, setpoint, "24"),
                sample(now, presence, true, acStatus, true, setpoint, "24")));

        assertThat(result.matched()).isTrue();
        assertThat(result.reason()).contains("semi-open window").contains("max sample gap");
    }

    @Test
    void durationIsFalseWhenInsufficientInterruptedOrSamplingGapExists() {
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        ParametroDef acStatus = parameter("ac_status", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoModoAvaliacao.DURACAO, EventoOperadorLogico.ALL, 3_600, 1_800,
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1),
                booleanCondition(acStatus, RegraOperador.EQ, true, true, true, 2));

        assertThat(engine.evaluate(context(event,
                sample(now.minusSeconds(600), presence, true, acStatus, true),
                sample(now, presence, true, acStatus, true))).matched()).isFalse();

        assertThat(engine.evaluate(context(event,
                sample(now.minusSeconds(1_800), presence, true, acStatus, true),
                sample(now.minusSeconds(1_200), presence, true, acStatus, false),
                sample(now, presence, true, acStatus, true))).matched()).isFalse();

        DroolsMissionRuleEngine strictGapEngine = new DroolsMissionRuleEngine(100, 300);
        assertThat(strictGapEngine.evaluate(context(event,
                sample(now.minusSeconds(1_800), presence, true, acStatus, true),
                sample(now.minusSeconds(1_200), presence, true, acStatus, true),
                sample(now, presence, true, acStatus, true))).matched()).isFalse();
    }

    @Test
    void ultimoAApagarTemporalScenarioIsTechnicalOnly() {
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        ParametroDef lightStatus = parameter("light_status", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoModoAvaliacao.DURACAO, EventoOperadorLogico.ALL, 120, 60,
                booleanCondition(presence, RegraOperador.EQ, false, true, true, 1),
                booleanCondition(lightStatus, RegraOperador.EQ, false, true, true, 2));

        MissionRuleEvaluationResult result = engine.evaluate(context(event,
                sample(now.minusSeconds(119), presence, false, lightStatus, false),
                sample(now.minusSeconds(60), presence, false, lightStatus, false),
                sample(now, presence, false, lightStatus, false)));

        assertThat(result.matched()).isTrue();
        assertThat(result.eventDefinitionId()).isEqualTo(event.getId());
    }

    @Test
    void outOfOrderFactsAreSortedAndReprocessingIsDeterministicWithPseudoClock() {
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoModoAvaliacao.DURACAO, EventoOperadorLogico.ALL, 2_000, 1_200,
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1));
        MissionEvaluationContext context = context(event,
                booleanFact(presence, true, now, "sensor-1", "room-1"),
                booleanFact(presence, true, now.minusSeconds(600), "sensor-1", "room-1"),
                booleanFact(presence, true, now.minusSeconds(1_200), "sensor-1", "room-1"));

        MissionRuleEvaluationResult first = engine.evaluate(context);
        MissionRuleEvaluationResult second = engine.evaluate(context);

        assertThat(first.matched()).isTrue();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void rejectsMixedRoomsAndSensorsToKeepEvaluationIsolated() {
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoModoAvaliacao.INSTANTANEO, EventoOperadorLogico.ALL, null, null,
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1));

        assertThatThrownBy(() -> engine.evaluate(context(event,
                booleanFact(presence, true, now, "sensor-1", "room-1"),
                booleanFact(parameter("other_presence", DataType.BOOLEAN), true, now, "sensor-2", "room-2"))))
                .isInstanceOf(DroolsMissionRuleException.class)
                .hasMessageContaining("mix sensors or compartments");
    }

    @Test
    void concurrentEvaluationsDoNotShareSessionState() throws Exception {
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoModoAvaliacao.INSTANTANEO, EventoOperadorLogico.ALL, null, null,
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1));
        Callable<Boolean> task = () -> engine.evaluate(context(event,
                booleanFact(presence, true, now, "sensor-1", "room-1"))).matched();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(task, task));
            assertThat(results.get(0).get()).isTrue();
            assertThat(results.get(1).get()).isTrue();
        }
    }

    @Test
    void invalidRuleAndFactLimitFailExplicitly() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        EventoDefinicao invalid = event(EventoModoAvaliacao.INSTANTANEO, EventoOperadorLogico.ALL, null, null,
                numericCondition(temperature, RegraOperador.CONTAINS, "20", null, true, true, 1));
        assertThatThrownBy(() -> engine.evaluate(context(invalid, numericFact(temperature, "21", now, "sensor-1", "room-1"))))
                .isInstanceOf(DroolsMissionRuleException.class)
                .hasMessageContaining("CONTAINS is not supported");

        DroolsMissionRuleEngine limited = new DroolsMissionRuleEngine(1, 900);
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao valid = event(EventoModoAvaliacao.DURACAO, EventoOperadorLogico.ALL, 120, 60,
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1));
        assertThatThrownBy(() -> limited.evaluate(context(valid,
                booleanFact(presence, true, now.minusSeconds(60), "sensor-1", "room-1"),
                booleanFact(presence, true, now, "sensor-1", "room-1"))))
                .isInstanceOf(DroolsMissionRuleException.class)
                .hasMessageContaining("fact limit");
    }

    @Test
    void nearFactLimitEvaluatesDeterministically() {
        DroolsMissionRuleEngine nearLimitEngine = new DroolsMissionRuleEngine(100, 900);
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoModoAvaliacao.DURACAO, EventoOperadorLogico.ALL, 6_000, 5_880,
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1));
        List<MeasurementFact> facts = new ArrayList<>();
        for (int i = 98; i >= 0; i--) {
            facts.add(booleanFact(presence, true, now.minusSeconds(i * 60L), "sensor-1", "room-1"));
        }
        MissionEvaluationContext context = new MissionEvaluationContext(now, event, Optional.empty(), facts);

        MissionRuleEvaluationResult first = nearLimitEngine.evaluate(context);
        MissionRuleEvaluationResult second = nearLimitEngine.evaluate(context);

        assertThat(first.matched()).isTrue();
        assertThat(second).isEqualTo(first);
    }

    private void assertEquivalent(MissionRuleEvaluationResult drools, MissionRuleEvaluationResult simpleResult) {
        assertThat(drools.eventDefinitionId()).isEqualTo(simpleResult.eventDefinitionId());
        assertThat(drools.matched()).isEqualTo(simpleResult.matched());
        assertThat(drools.evaluatedAt()).isEqualTo(simpleResult.evaluatedAt());
        assertThat(drools.conditionResults()).usingRecursiveComparison()
                .ignoringFields("reason")
                .isEqualTo(simpleResult.conditionResults());
        assertThat(drools.evidences()).isEqualTo(simpleResult.evidences());
    }

    private MissionEvaluationContext context(EventoDefinicao event, MeasurementFact... facts) {
        return new MissionEvaluationContext(now, event, Optional.empty(), List.of(facts));
    }

    private static MeasurementFact[] sample(
            Instant measuredAt,
            ParametroDef booleanOne,
            Boolean booleanOneValue,
            ParametroDef booleanTwo,
            Boolean booleanTwoValue
    ) {
        return new MeasurementFact[] {
                booleanFact(booleanOne, booleanOneValue, measuredAt, "sensor-1", "room-1"),
                booleanFact(booleanTwo, booleanTwoValue, measuredAt, "sensor-1", "room-1")
        };
    }

    private static MeasurementFact[] sample(
            Instant measuredAt,
            ParametroDef booleanOne,
            Boolean booleanOneValue,
            ParametroDef booleanTwo,
            Boolean booleanTwoValue,
            ParametroDef numeric,
            String numericValue
    ) {
        return new MeasurementFact[] {
                booleanFact(booleanOne, booleanOneValue, measuredAt, "sensor-1", "room-1"),
                booleanFact(booleanTwo, booleanTwoValue, measuredAt, "sensor-1", "room-1"),
                numericFact(numeric, numericValue, measuredAt, "sensor-1", "room-1")
        };
    }

    private MissionEvaluationContext context(EventoDefinicao event, MeasurementFact[]... samples) {
        List<MeasurementFact> facts = new ArrayList<>();
        for (MeasurementFact[] sample : samples) {
            facts.addAll(List.of(sample));
        }
        return new MissionEvaluationContext(now, event, Optional.empty(), facts);
    }

    private static EventoDefinicao event(
            EventoModoAvaliacao mode,
            EventoOperadorLogico operadorLogico,
            Integer janelaSegundos,
            Integer duracaoMinimaSegundos,
            EventoCondicao... conditions
    ) {
        EventoDefinicao event = new EventoDefinicao();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setNome("Evento");
        event.setTipoDisparo(EventoTipoDisparo.MEDICAO_RECEBIDA);
        event.setModoAvaliacao(mode);
        event.setOperadorLogico(operadorLogico);
        event.setPoliticaAtribuicao(EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA);
        event.setJanelaSegundos(janelaSegundos);
        event.setDuracaoMinimaSegundos(duracaoMinimaSegundos);
        event.setQuantidadeNecessaria(1);
        event.setAtivo(true);
        for (EventoCondicao condition : conditions) {
            condition.setEventoDefinicao(event);
        }
        event.setCondicoes(List.of(conditions));
        return event;
    }

    private static EventoCondicao numericCondition(
            ParametroDef parameter,
            RegraOperador operator,
            String value1,
            String value2,
            boolean required,
            boolean active,
            int order
    ) {
        EventoCondicao condition = baseCondition(parameter, operator, required, active, order);
        condition.setValorNumeric1(value1 == null ? null : new BigDecimal(value1));
        condition.setValorNumeric2(value2 == null ? null : new BigDecimal(value2));
        return condition;
    }

    private static EventoCondicao booleanCondition(
            ParametroDef parameter,
            RegraOperador operator,
            Boolean value,
            boolean required,
            boolean active,
            int order
    ) {
        EventoCondicao condition = baseCondition(parameter, operator, required, active, order);
        condition.setValorBoolean(value);
        return condition;
    }

    private static EventoCondicao textCondition(
            ParametroDef parameter,
            RegraOperador operator,
            String value,
            boolean required,
            boolean active,
            int order
    ) {
        EventoCondicao condition = baseCondition(parameter, operator, required, active, order);
        condition.setValorText(value);
        return condition;
    }

    private static EventoCondicao baseCondition(
            ParametroDef parameter,
            RegraOperador operator,
            boolean required,
            boolean active,
            int order
    ) {
        EventoCondicao condition = new EventoCondicao();
        ReflectionTestUtils.setField(condition, "id", UUID.randomUUID());
        condition.setParametroDef(parameter);
        condition.setOperador(operator);
        condition.setObrigatoria(required);
        condition.setAtivo(active);
        condition.setOrdem(order);
        return condition;
    }

    private static MeasurementFact numericFact(
            ParametroDef parameter,
            String value,
            Instant measuredAt,
            String sensorExternalId,
            String compartimentoId
    ) {
        return fact(parameter, value == null ? null : new BigDecimal(value), null, null, measuredAt, sensorExternalId, compartimentoId);
    }

    private static MeasurementFact booleanFact(
            ParametroDef parameter,
            Boolean value,
            Instant measuredAt,
            String sensorExternalId,
            String compartimentoId
    ) {
        return fact(parameter, null, value, null, measuredAt, sensorExternalId, compartimentoId);
    }

    private static MeasurementFact textFact(
            ParametroDef parameter,
            String value,
            Instant measuredAt,
            String sensorExternalId,
            String compartimentoId
    ) {
        return fact(parameter, null, null, value, measuredAt, sensorExternalId, compartimentoId);
    }

    private static MeasurementFact fact(
            ParametroDef parameter,
            BigDecimal numericValue,
            Boolean booleanValue,
            String textValue,
            Instant measuredAt,
            String sensorExternalId,
            String compartimentoId
    ) {
        return new MeasurementFact(
                UUID.randomUUID(),
                UUID.randomUUID(),
                parameter.getId(),
                parameter.getNome(),
                parameter.getDataType(),
                numericValue,
                booleanValue,
                textValue,
                measuredAt,
                sensorExternalId,
                compartimentoId
        );
    }

    private static ParametroDef parameter(String name, DataType dataType) {
        ParametroDef parameter = new ParametroDef(new TipoDeSensor("TYPE-" + UUID.randomUUID()), name, null, dataType, null);
        ReflectionTestUtils.setField(parameter, "id", UUID.randomUUID());
        return parameter;
    }
}
