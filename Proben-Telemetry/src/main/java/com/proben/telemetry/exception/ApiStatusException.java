package com.proben.telemetry.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception that carries an HTTP status and stable API error code.
 */

public class ApiStatusException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    /** @param status HTTP status to return @param error stable error code @param message human-readable detail */
    public ApiStatusException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    /** @return HTTP status associated with this failure */
    public HttpStatus getStatus() {
        return status;
    }

    /** @return stable API error code */
    public String getError() {
        return error;
    }
}
