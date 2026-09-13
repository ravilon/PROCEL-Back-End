package com.procel.api.service.missions.rules.drools;

import com.procel.api.entity.sensors.DataType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class DroolsMeasurementFact {
    private final UUID medicaoId;
    private final UUID parametroValorId;
    private final UUID parametroDefId;
    private final String parametroNome;
    private final DataType dataType;
    private final BigDecimal numericValue;
    private final Boolean booleanValue;
    private final String textValue;
    private final Instant measuredAt;
    private final String sensorExternalId;
    private final String compartimentoId;

    public DroolsMeasurementFact(
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
        this.medicaoId = medicaoId;
        this.parametroValorId = parametroValorId;
        this.parametroDefId = parametroDefId;
        this.parametroNome = parametroNome;
        this.dataType = dataType;
        this.numericValue = numericValue;
        this.booleanValue = booleanValue;
        this.textValue = textValue;
        this.measuredAt = measuredAt;
        this.sensorExternalId = sensorExternalId;
        this.compartimentoId = compartimentoId;
    }

    public UUID getMedicaoId() { return medicaoId; }
    public UUID getParametroValorId() { return parametroValorId; }
    public UUID getParametroDefId() { return parametroDefId; }
    public String getParametroNome() { return parametroNome; }
    public DataType getDataType() { return dataType; }
    public BigDecimal getNumericValue() { return numericValue; }
    public Boolean getBooleanValue() { return booleanValue; }
    public String getTextValue() { return textValue; }
    public Instant getMeasuredAt() { return measuredAt; }
    public String getSensorExternalId() { return sensorExternalId; }
    public String getCompartimentoId() { return compartimentoId; }

    public UUID medicaoId() { return medicaoId; }
    public UUID parametroValorId() { return parametroValorId; }
    public UUID parametroDefId() { return parametroDefId; }
    public String parametroNome() { return parametroNome; }
    public DataType dataType() { return dataType; }
    public BigDecimal numericValue() { return numericValue; }
    public Boolean booleanValue() { return booleanValue; }
    public String textValue() { return textValue; }
    public Instant measuredAt() { return measuredAt; }
    public String sensorExternalId() { return sensorExternalId; }
    public String compartimentoId() { return compartimentoId; }

    public String observedValue() {
        return switch (dataType) {
            case NUMERIC -> numericValue == null ? null : numericValue.toPlainString();
            case BOOLEAN -> booleanValue == null ? null : booleanValue.toString();
            case TEXT -> textValue;
        };
    }
}
