package com.proben.telemetry.service.mqtt;

/**
 * Signals a malformed MQTT topic or payload that should be rejected without retry.
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
