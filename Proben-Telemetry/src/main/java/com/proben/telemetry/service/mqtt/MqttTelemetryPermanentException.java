package com.proben.telemetry.service.mqtt;

/**
 * Excecao que representa falhas relacionadas a mqtt telemetry permanent.
 */

public class MqttTelemetryPermanentException extends RuntimeException {
    private final String code;

    public MqttTelemetryPermanentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
