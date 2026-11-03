package com.proben.api.exception;

import org.springframework.http.HttpStatus;

public class SensorIntegrationException extends ApiStatusException {
    public SensorIntegrationException(HttpStatus status, String error, String message) {
        super(status, error, message);
    }
}
