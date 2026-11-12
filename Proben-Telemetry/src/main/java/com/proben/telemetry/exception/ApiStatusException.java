package com.proben.telemetry.exception;

import org.springframework.http.HttpStatus;

/**
 * Excecao que representa falhas relacionadas a api status.
 */

public class ApiStatusException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public ApiStatusException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }
}
