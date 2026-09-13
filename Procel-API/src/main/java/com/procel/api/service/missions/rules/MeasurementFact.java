package com.procel.api.service.missions.rules;

import com.procel.api.entity.sensors.DataType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeasurementFact(
        UUID medicaoId,
        UUID parametroValorId,
        UUID parametroDefId,
        String parametroNome,
        DataType dataType,
        BigDecimal numericValue,
        Boolean booleanValue,
        String textValue,
        Instant measuredAt,
        String sensorExternalId,
        String compartimentoId
) {
    public MeasurementFact {
        if (parametroDefId == null) throw new IllegalArgumentException("parametroDefId is required");
        if (dataType == null) throw new IllegalArgumentException("dataType is required");
    }

    public String observedValue() {
        return switch (dataType) {
            case NUMERIC -> numericValue == null ? null : numericValue.toPlainString();
            case BOOLEAN -> booleanValue == null ? null : booleanValue.toString();
            case TEXT -> textValue;
        };
    }
}
