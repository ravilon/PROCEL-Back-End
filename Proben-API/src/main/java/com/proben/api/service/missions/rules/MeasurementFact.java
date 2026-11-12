package com.proben.api.service.missions.rules;

import com.proben.api.entity.sensors.DataType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Valores tipados de uma medição disponibilizados ao motor de regras.
 *
 * @param medicaoId identificador da medição de origem
 * @param parametroValorId identificador do valor de parâmetro de origem
 * @param parametroDefId identificador da definição do parâmetro
 * @param parametroNome nome do parâmetro
 * @param dataType tipo de dado do valor
 * @param numericValue valor numérico, quando aplicável
 * @param booleanValue valor booleano, quando aplicável
 * @param textValue valor textual, quando aplicável
 * @param measuredAt instante da medição
 * @param sensorExternalId identificador externo do sensor
 * @param compartimentoId identificador do compartimento do sensor
 */

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

    /** Retorna como texto o valor correspondente ao tipo de dado do parâmetro. */
    public String observedValue() {
        return switch (dataType) {
            case NUMERIC -> numericValue == null ? null : numericValue.toPlainString();
            case BOOLEAN -> booleanValue == null ? null : booleanValue.toString();
            case TEXT -> textValue;
        };
    }
}
