package com.proben.api.exception;

/**
 * Excecao que representa falhas relacionadas a not found.
 */

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}