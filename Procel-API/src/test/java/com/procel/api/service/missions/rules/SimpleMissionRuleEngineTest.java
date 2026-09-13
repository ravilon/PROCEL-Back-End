package com.procel.api.service.missions.rules;

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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleMissionRuleEngineTest {

    private final SimpleMissionRuleEngine engine = new SimpleMissionRuleEngine();
    private final Instant now = Instant.parse("2026-09-13T12:00:00Z");

    @Test
    void allMatchesWhenAllRequiredConditionsMatch() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 2),
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1));

        var result = engine.evaluate(context(event,
                numericFact(temperature, "21"),
                booleanFact(presence, true)));

        assertThat(result.matched()).isTrue();
        assertThat(result.conditionResults()).extracting(ConditionEvaluationResult::matched)
                .containsExactly(true, true);
        assertThat(result.conditionResults()).extracting(ConditionEvaluationResult::eventoCondicaoId)
                .containsExactly(event.getCondicoes().get(0).getId(), event.getCondicoes().get(1).getId());
    }

    @Test
    void allDoesNotMatchWhenOneRequiredConditionDoesNotMatch() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1),
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 2));

        var result = engine.evaluate(context(event,
                numericFact(temperature, "19"),
                booleanFact(presence, true)));

        assertThat(result.matched()).isFalse();
        assertThat(result.reason()).contains("did not match");
    }

    @Test
    void anyMatchesWhenOneRequiredConditionMatches() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        ParametroDef state = parameter("state", DataType.TEXT);
        EventoDefinicao event = event(EventoOperadorLogico.ANY,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1),
                textCondition(state, RegraOperador.EQ, "ALERT", true, true, 2));

        var result = engine.evaluate(context(event,
                numericFact(temperature, "19"),
                textFact(state, "ALERT")));

        assertThat(result.matched()).isTrue();
    }

    @Test
    void anyDoesNotMatchWhenNoRequiredConditionMatches() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        ParametroDef state = parameter("state", DataType.TEXT);
        EventoDefinicao event = event(EventoOperadorLogico.ANY,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1),
                textCondition(state, RegraOperador.EQ, "ALERT", true, true, 2));

        var result = engine.evaluate(context(event,
                numericFact(temperature, "19"),
                textFact(state, "OK")));

        assertThat(result.matched()).isFalse();
    }

    @Test
    void optionalConditionDoesNotBlockOverallResult() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        ParametroDef state = parameter("state", DataType.TEXT);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1),
                textCondition(state, RegraOperador.EQ, "ALERT", false, true, 2));

        var result = engine.evaluate(context(event,
                numericFact(temperature, "21"),
                textFact(state, "OK")));

        assertThat(result.matched()).isTrue();
        assertThat(result.conditionResults()).extracting(ConditionEvaluationResult::matched)
                .containsExactly(true, false);
    }

    @Test
    void inactiveEventDoesNotMatchAndInactiveConditionIsIgnored() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        EventoDefinicao inactiveEvent = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1));
        inactiveEvent.setAtivo(false);

        var inactiveEventResult = engine.evaluate(context(inactiveEvent, numericFact(temperature, "21")));

        assertThat(inactiveEventResult.matched()).isFalse();
        assertThat(inactiveEventResult.conditionResults()).isEmpty();

        EventoDefinicao onlyInactiveCondition = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, false, 1));

        var inactiveConditionResult = engine.evaluate(context(onlyInactiveCondition, numericFact(temperature, "21")));

        assertThat(inactiveConditionResult.matched()).isFalse();
        assertThat(inactiveConditionResult.reason()).isEqualTo("No active required conditions");
        assertThat(inactiveConditionResult.conditionResults()).isEmpty();
    }

    @Test
    void noActiveRequiredConditionsDoesNotMatch() {
        ParametroDef state = parameter("state", DataType.TEXT);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                textCondition(state, RegraOperador.EQ, "OK", false, true, 1));

        var result = engine.evaluate(context(event, textFact(state, "OK")));

        assertThat(result.matched()).isFalse();
        assertThat(result.conditionResults()).hasSize(1);
        assertThat(result.reason()).isEqualTo("No active required conditions");
    }

    @Test
    void missingParameterProducesExplicitUnmatchedCondition() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1));

        var result = engine.evaluate(context(event));

        assertThat(result.matched()).isFalse();
        assertThat(result.conditionResults().getFirst().reason()).contains("Missing parameter fact");
        assertThat(result.conditionResults().getFirst().parametroValorId()).isEmpty();
    }

    @Test
    void duplicateParameterFactsAreRejectedAsAmbiguous() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1));

        assertThatThrownBy(() -> engine.evaluate(context(event,
                numericFact(temperature, "21"),
                numericFact(temperature, "22"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ambiguous");
    }

    @Test
    void numericOperatorsAreEvaluatedWithBigDecimalCompareTo() {
        assertNumeric(RegraOperador.GT, "2", null, "3", true);
        assertNumeric(RegraOperador.GTE, "2", null, "2", true);
        assertNumeric(RegraOperador.LT, "2", null, "1", true);
        assertNumeric(RegraOperador.LTE, "2", null, "2", true);
        assertNumeric(RegraOperador.EQ, "2.0", null, "2.00", true);
        assertNumeric(RegraOperador.NEQ, "2", null, "3", true);
    }

    @Test
    void numericBetweenIsInclusiveAndOutsideIsStrictlyOutside() {
        assertNumeric(RegraOperador.BETWEEN, "10", "20", "10", true);
        assertNumeric(RegraOperador.BETWEEN, "10", "20", "20", true);
        assertNumeric(RegraOperador.OUTSIDE, "10", "20", "9", true);
        assertNumeric(RegraOperador.OUTSIDE, "10", "20", "21", true);
        assertNumeric(RegraOperador.OUTSIDE, "10", "20", "10", false);
    }

    @Test
    void booleanOperatorsAreEvaluated() {
        ParametroDef presence = parameter("presence", DataType.BOOLEAN);
        EventoDefinicao eqEvent = event(EventoOperadorLogico.ALL,
                booleanCondition(presence, RegraOperador.EQ, true, true, true, 1));
        EventoDefinicao neqEvent = event(EventoOperadorLogico.ALL,
                booleanCondition(presence, RegraOperador.NEQ, false, true, true, 1));

        assertThat(engine.evaluate(context(eqEvent, booleanFact(presence, true))).matched()).isTrue();
        assertThat(engine.evaluate(context(neqEvent, booleanFact(presence, true))).matched()).isTrue();
    }

    @Test
    void textOperatorsAreEvaluated() {
        ParametroDef state = parameter("state", DataType.TEXT);
        assertText(RegraOperador.EQ, "ALERT", "ALERT", true);
        assertText(RegraOperador.NEQ, "ALERT", "OK", true);
        assertText(RegraOperador.CONTAINS, "LER", "ALERT", true);

        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                textCondition(state, RegraOperador.CONTAINS, "ler", true, true, 1));
        assertThat(engine.evaluate(context(event, textFact(state, "ALERT"))).matched()).isFalse();
    }

    @Test
    void nullObservedValueDoesNotMatch() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1));

        var result = engine.evaluate(context(event, fact(temperature, null, null, null)));

        assertThat(result.matched()).isFalse();
        assertThat(result.conditionResults().getFirst().reason()).contains("null");
    }

    @Test
    void unsupportedTriggerAndModeAreRejected() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        EventoDefinicao unsupportedTrigger = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1));
        unsupportedTrigger.setTipoDisparo(EventoTipoDisparo.AULA_INICIADA);

        assertThatThrownBy(() -> engine.evaluate(context(unsupportedTrigger, numericFact(temperature, "21"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported event trigger type");

        EventoDefinicao unsupportedMode = event(EventoOperadorLogico.ALL,
                numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1));
        unsupportedMode.setModoAvaliacao(EventoModoAvaliacao.AGREGADO);

        assertThatThrownBy(() -> engine.evaluate(context(unsupportedMode, numericFact(temperature, "21"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported evaluation mode");
    }

    @Test
    void evidencesPointToMeasurementAndParameterValueAndInputsAreNotMutated() {
        ParametroDef temperature = parameter("temperature", DataType.NUMERIC);
        EventoCondicao condition = numericCondition(temperature, RegraOperador.GT, "20", null, true, true, 1);
        EventoDefinicao event = event(EventoOperadorLogico.ALL, condition);
        MeasurementFact fact = numericFact(temperature, "21");
        int originalOrder = condition.getOrdem();
        boolean originalActive = condition.isAtivo();

        var result = engine.evaluate(context(event, fact));

        assertThat(result.evidences()).containsExactly(fact);
        assertThat(result.evidences().getFirst().medicaoId()).isEqualTo(fact.medicaoId());
        assertThat(result.conditionResults().getFirst().parametroValorId()).contains(fact.parametroValorId());
        assertThat(condition.getOrdem()).isEqualTo(originalOrder);
        assertThat(condition.isAtivo()).isEqualTo(originalActive);
        assertThat(event.getCondicoes()).containsExactly(condition);
    }

    private void assertNumeric(
            RegraOperador operator,
            String expected1,
            String expected2,
            String observed,
            boolean expectedMatched
    ) {
        ParametroDef parameter = parameter("numeric-" + operator + UUID.randomUUID(), DataType.NUMERIC);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                numericCondition(parameter, operator, expected1, expected2, true, true, 1));

        assertThat(engine.evaluate(context(event, numericFact(parameter, observed))).matched())
                .isEqualTo(expectedMatched);
    }

    private void assertText(RegraOperador operator, String expected, String observed, boolean expectedMatched) {
        ParametroDef parameter = parameter("text-" + operator + UUID.randomUUID(), DataType.TEXT);
        EventoDefinicao event = event(EventoOperadorLogico.ALL,
                textCondition(parameter, operator, expected, true, true, 1));

        assertThat(engine.evaluate(context(event, textFact(parameter, observed))).matched())
                .isEqualTo(expectedMatched);
    }

    private MissionEvaluationContext context(EventoDefinicao event, MeasurementFact... facts) {
        return new MissionEvaluationContext(now, event, Optional.empty(), List.of(facts));
    }

    private static EventoDefinicao event(EventoOperadorLogico operadorLogico, EventoCondicao... conditions) {
        EventoDefinicao event = new EventoDefinicao();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setNome("Evento");
        event.setTipoDisparo(EventoTipoDisparo.MEDICAO_RECEBIDA);
        event.setModoAvaliacao(EventoModoAvaliacao.INSTANTANEO);
        event.setOperadorLogico(operadorLogico);
        event.setPoliticaAtribuicao(EventoPoliticaAtribuicao.SEM_ATRIBUICAO_AUTOMATICA);
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

    private static MeasurementFact numericFact(ParametroDef parameter, String value) {
        return fact(parameter, value == null ? null : new BigDecimal(value), null, null);
    }

    private static MeasurementFact booleanFact(ParametroDef parameter, Boolean value) {
        return fact(parameter, null, value, null);
    }

    private static MeasurementFact textFact(ParametroDef parameter, String value) {
        return fact(parameter, null, null, value);
    }

    private static MeasurementFact fact(
            ParametroDef parameter,
            BigDecimal numericValue,
            Boolean booleanValue,
            String textValue
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
                Instant.parse("2026-09-13T11:59:00Z"),
                "sensor-1",
                "room-1"
        );
    }

    private static ParametroDef parameter(String name, DataType dataType) {
        ParametroDef parameter = new ParametroDef(new TipoDeSensor("TYPE-" + UUID.randomUUID()), name, null, dataType, null);
        ReflectionTestUtils.setField(parameter, "id", UUID.randomUUID());
        return parameter;
    }
}
