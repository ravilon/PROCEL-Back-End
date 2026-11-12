package com.proben.api.exception;

/**
 * Excecao que representa falhas relacionadas a unauthorized.
 */

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
