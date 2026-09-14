package com.procel.api.benchmark;

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
import com.procel.api.service.missions.rules.MeasurementFact;
import com.procel.api.service.missions.rules.MissionEvaluationContext;
import com.procel.api.service.missions.rules.MissionRuleEvaluationResult;
import com.procel.api.service.missions.rules.SimpleMissionRuleEngine;
import com.procel.api.service.missions.rules.drools.DroolsMissionRuleEngine;
import com.procel.api.service.missions.rules.drools.DroolsRuleEngineSettings;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.SampleTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DroolsMissionRuleEngineBenchmark {

    @Benchmark
    public MissionRuleEvaluationResult simpleInstant(InstantState state) {
        return state.simple.evaluate(state.context);
    }

    @Benchmark
    public MissionRuleEvaluationResult droolsInstantCold(InstantState state) {
        return state.newDrools().evaluate(state.context);
    }

    @Benchmark
    public MissionRuleEvaluationResult droolsInstantWarm(InstantState state) {
        return state.drools.evaluate(state.context);
    }

    @Benchmark
    public MissionRuleEvaluationResult droolsDuration(DurationState state) {
        return state.drools.evaluate(state.context);
    }

    @Benchmark
    @Threads(1)
    public MissionRuleEvaluationResult droolsConcurrentOneThread(InstantState state) {
        return state.drools.evaluate(state.context);
    }

    @Benchmark
    @Threads(4)
    public MissionRuleEvaluationResult droolsConcurrentFourThreads(InstantState state) {
        return state.drools.evaluate(state.context);
    }

    @Benchmark
    @Threads(8)
    public MissionRuleEvaluationResult droolsConcurrentEightThreads(InstantState state) {
        return state.drools.evaluate(state.context);
    }

    @State(Scope.Benchmark)
    public static class InstantState {
        @Param({"10", "100", "1000"})
        public int facts;

        private final SimpleMissionRuleEngine simple = new SimpleMissionRuleEngine();
        private final Instant now = Instant.parse("2026-09-13T12:00:00Z");
        private DroolsMissionRuleEngine drools;
        private MissionEvaluationContext context;

        @Setup(Level.Trial)
        public void setup() {
            ParametroDef target = parameter("target", DataType.NUMERIC, 0);
            EventoDefinicao event = event(EventoModoAvaliacao.INSTANTANEO, EventoOperadorLogico.ALL, null, null,
                    numericCondition(target, RegraOperador.BETWEEN, "23", "25", true, true, 1));
            List<MeasurementFact> measurements = new ArrayList<>();
            measurements.add(numericFact(target, "24", now, 0));
            for (int i = 1; i < facts; i++) {
                ParametroDef extra = parameter("extra_" + i, DataType.NUMERIC, i);
                measurements.add(numericFact(extra, "99", now.minusMillis(i), i));
            }
            context = new MissionEvaluationContext(now, event, Optional.empty(), List.copyOf(measurements));
            drools = newDrools();
            drools.evaluate(context);
        }

        private DroolsMissionRuleEngine newDrools() {
            return new DroolsMissionRuleEngine(settings(), null);
        }
    }

    @State(Scope.Benchmark)
    public static class DurationState {
        @Param({"100", "1000", "5000"})
        public int facts;

        private final Instant now = Instant.parse("2026-09-13T12:00:00Z");
        private DroolsMissionRuleEngine drools;
        private MissionEvaluationContext context;

        @Setup(Level.Trial)
        public void setup() {
            ParametroDef presence = parameter("presence", DataType.BOOLEAN, 0);
            EventoDefinicao event = event(EventoModoAvaliacao.DURACAO, EventoOperadorLogico.ALL, 24 * 60 * 60, 1_800,
                    booleanCondition(presence, RegraOperador.EQ, true, true, true, 1));
            List<MeasurementFact> measurements = new ArrayList<>(facts);
            for (int i = facts - 1; i >= 0; i--) {
                measurements.add(booleanFact(presence, true, now.minusSeconds(i), i));
            }
            context = new MissionEvaluationContext(now, event, Optional.empty(), List.copyOf(measurements));
            drools = new DroolsMissionRuleEngine(settings(), null);
            drools.evaluate(context);
        }
    }

    private static DroolsRuleEngineSettings settings() {
        return new DroolsRuleEngineSettings(
                5_000,
                200,
                Duration.ofMinutes(30),
                Duration.ofSeconds(60),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofHours(24)
        );
    }

    private static EventoDefinicao event(
            EventoModoAvaliacao mode,
            EventoOperadorLogico operadorLogico,
            Integer janelaSegundos,
            Integer duracaoMinimaSegundos,
            EventoCondicao... conditions
    ) {
        EventoDefinicao event = new EventoDefinicao();
        ReflectionTestUtils.setField(event, "id", uuid(1));
        event.setNome("Benchmark");
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

    private static EventoCondicao baseCondition(
            ParametroDef parameter,
            RegraOperador operator,
            boolean required,
            boolean active,
            int order
    ) {
        EventoCondicao condition = new EventoCondicao();
        ReflectionTestUtils.setField(condition, "id", uuid(100 + order));
        condition.setParametroDef(parameter);
        condition.setOperador(operator);
        condition.setObrigatoria(required);
        condition.setAtivo(active);
        condition.setOrdem(order);
        return condition;
    }

    private static MeasurementFact numericFact(ParametroDef parameter, String value, Instant measuredAt, int index) {
        return new MeasurementFact(
                uuid(10_000 + index),
                uuid(20_000 + index),
                parameter.getId(),
                parameter.getNome(),
                parameter.getDataType(),
                new BigDecimal(value),
                null,
                null,
                measuredAt,
                "sensor-1",
                "room-1"
        );
    }

    private static MeasurementFact booleanFact(ParametroDef parameter, Boolean value, Instant measuredAt, int index) {
        return new MeasurementFact(
                uuid(30_000 + index),
                uuid(40_000 + index),
                parameter.getId(),
                parameter.getNome(),
                parameter.getDataType(),
                null,
                value,
                null,
                measuredAt,
                "sensor-1",
                "room-1"
        );
    }

    private static ParametroDef parameter(String name, DataType dataType, int index) {
        ParametroDef parameter = new ParametroDef(new TipoDeSensor("BENCHMARK"), name, null, dataType, null);
        ReflectionTestUtils.setField(parameter, "id", uuid(50_000 + index));
        return parameter;
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }
}
