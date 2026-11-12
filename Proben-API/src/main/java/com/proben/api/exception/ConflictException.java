package com.proben.api.exception;

/**
 * Excecao que representa falhas relacionadas a conflict.
 */

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}