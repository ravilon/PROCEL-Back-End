package com.proben.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Excecao que representa falhas relacionadas a sensor integration.
 */

public class SensorIntegrationException extends ApiStatusException {
    public SensorIntegrationException(HttpStatus status, String error, String message) {
        super(status, error, message);
    }
}
