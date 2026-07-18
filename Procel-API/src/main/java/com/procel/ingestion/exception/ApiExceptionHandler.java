package com.procel.ingestion.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String message, String error, Instant timestamp) {}

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(NotFoundException ex) {
        return new ErrorResponse(ex.getMessage(), "NOT_FOUND", Instant.now());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(ConflictException ex) {
        return new ErrorResponse(ex.getMessage(), "CONFLICT", Instant.now());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse unauthorized(UnauthorizedException ex) {
        return new ErrorResponse(ex.getMessage(), "UNAUTHORIZED", Instant.now());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage(), "BAD_REQUEST", Instant.now());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse dataIntegrity(DataIntegrityViolationException ex) {
        // mensagem genérica para não vazar detalhes do banco
        return new ErrorResponse("Constraint violation", "CONFLICT", Instant.now());
    }
}
