package com.procel.api.service.missions.rules;

import com.procel.api.entity.rooms.Compartimento;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.ParametroValor;
import com.procel.api.entity.sensors.Sensor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MeasurementFactFactory {

    public List<MeasurementFact> from(Medicao medicao, List<ParametroValor> valores) {
        if (medicao == null) throw new IllegalArgumentException("medicao is required");
        if (valores == null) return List.of();
        return valores.stream()
                .map(valor -> from(medicao, valor))
                .toList();
    }

    public MeasurementFact from(ParametroValor valor) {
        if (valor == null) throw new IllegalArgumentException("parametroValor is required");
        return from(valor.getMedicao(), valor);
    }

    public MeasurementFact from(Medicao medicao, ParametroValor valor) {
        if (medicao == null) throw new IllegalArgumentException("medicao is required");
        if (valor == null) throw new IllegalArgumentException("parametroValor is required");
        ParametroDef parametroDef = valor.getParametroDef();
        if (parametroDef == null) throw new IllegalArgumentException("parametroDef is required");

        Sensor sensor = medicao.getSensor();
        Compartimento compartimento = sensor == null ? null : sensor.getCompartimento();
        return new MeasurementFact(
                medicao.getId(),
                valor.getId(),
                parametroDef.getId(),
                parametroDef.getNome(),
                parametroDef.getDataType(),
                valor.getNumericValue(),
                valor.getBooleanValue(),
                valor.getTextValue(),
                medicao.getTimestamp(),
                sensor == null ? null : sensor.getExternalId(),
                compartimento == null ? null : compartimento.getId()
        );
    }
}
