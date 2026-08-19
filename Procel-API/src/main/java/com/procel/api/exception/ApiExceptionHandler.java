package com.procel.api.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String message, String error, Instant timestamp) {}

    @ExceptionHandler(ApiStatusException.class)
    public ResponseEntity<ErrorResponse> apiStatus(ApiStatusException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getMessage(), ex.getError(), Instant.now()));
    }

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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String error = status.value() == 422 ? "UNPROCESSABLE_ENTITY" : status.name();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getReason(), error, Instant.now()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse missingParameter(MissingServletRequestParameterException ex) {
        return new ErrorResponse(ex.getParameterName() + " is required", "BAD_REQUEST", Instant.now());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse typeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse(ex.getName() + " is invalid", "BAD_REQUEST", Instant.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Invalid request";
        }
        return new ErrorResponse(message, "BAD_REQUEST", Instant.now());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadable(HttpMessageNotReadableException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid request body";
        String error = message.contains("MedicaoIngestaoSource") ? "SOURCE_INVALID" : "BAD_REQUEST";
        if (message.contains("Instant")) {
            error = "TIMESTAMP_INVALID";
        }
        return new ErrorResponse(message, error, Instant.now());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse dataIntegrity(DataIntegrityViolationException ex) {
        // mensagem genérica para não vazar detalhes do banco
        return new ErrorResponse("Constraint violation", "CONFLICT", Instant.now());
    }
}
