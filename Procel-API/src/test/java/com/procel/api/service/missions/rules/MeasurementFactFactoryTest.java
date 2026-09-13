package com.procel.api.service.missions.rules;

import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.ParametroValor;
import com.procel.api.entity.sensors.Sensor;
import com.procel.api.entity.sensors.TipoDeSensor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementFactFactoryTest {

    private final MeasurementFactFactory factory = new MeasurementFactFactory();

    @Test
    void mapsMeasurementAndParameterValuesToImmutableFacts() {
        TipoDeSensor sensorType = new TipoDeSensor("TYPE-" + UUID.randomUUID());
        Compartimento room = new Compartimento();
        room.setId("ROOM-101");
        Sensor sensor = new Sensor("SENSOR-1", "Sensor 1", sensorType, room);
        Instant measuredAt = Instant.parse("2026-09-13T12:30:00Z");
        Medicao medicao = new Medicao(sensor, measuredAt, measuredAt.plusSeconds(1), "test");
        UUID medicaoId = UUID.randomUUID();
        ReflectionTestUtils.setField(medicao, "id", medicaoId);

        ParametroDef parameter = new ParametroDef(sensorType, "temperature", null, DataType.NUMERIC, "C");
        UUID parametroDefId = UUID.randomUUID();
        ReflectionTestUtils.setField(parameter, "id", parametroDefId);

        ParametroValor value = new ParametroValor(medicao, parameter);
        UUID parametroValorId = UUID.randomUUID();
        ReflectionTestUtils.setField(value, "id", parametroValorId);
        value.setNumericValue(new BigDecimal("23.50"));

        List<MeasurementFact> facts = factory.from(medicao, List.of(value));

        assertThat(facts).hasSize(1);
        MeasurementFact fact = facts.getFirst();
        assertThat(fact.medicaoId()).isEqualTo(medicaoId);
        assertThat(fact.parametroValorId()).isEqualTo(parametroValorId);
        assertThat(fact.parametroDefId()).isEqualTo(parametroDefId);
        assertThat(fact.parametroNome()).isEqualTo("temperature");
        assertThat(fact.dataType()).isEqualTo(DataType.NUMERIC);
        assertThat(fact.numericValue()).isEqualByComparingTo("23.50");
        assertThat(fact.measuredAt()).isEqualTo(measuredAt);
        assertThat(fact.sensorExternalId()).isEqualTo("SENSOR-1");
        assertThat(fact.compartimentoId()).isEqualTo("ROOM-101");
    }
}
