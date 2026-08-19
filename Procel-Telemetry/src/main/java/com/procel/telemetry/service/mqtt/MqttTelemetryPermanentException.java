package com.procel.telemetry.service.mqtt;

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
