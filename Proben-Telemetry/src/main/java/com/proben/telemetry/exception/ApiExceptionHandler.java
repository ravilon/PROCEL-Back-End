package com.proben.telemetry.exception;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/** Maps telemetry API exceptions to stable HTTP error responses. */
@RestControllerAdvice
public class ApiExceptionHandler {
    /**
     * Error body returned to API clients.
     * @param message human-readable explanation of the error
     * @param error stable machine-readable error code
     * @param timestamp time when the API created this response
     */
    public record ErrorResponse(String message, String error, Instant timestamp) {}

    /** Creates the exception handler used by Spring MVC. */
    public ApiExceptionHandler() {}

    /** @param ex API failure to map @return response with the exception's status */
    @ExceptionHandler(ApiStatusException.class)
    public ResponseEntity<ErrorResponse> apiStatus(ApiStatusException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getMessage(), ex.getError(), Instant.now()));
    }

    /** @param ex validation or argument failure @return error body */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage(), "BAD_REQUEST", Instant.now());
    }

    /** @param ex binding result containing invalid fields @return error body */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) message = "Invalid request";
        return new ErrorResponse(message, "BAD_REQUEST", Instant.now());
    }

    /** @param ex request body deserialization failure @return error body */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadable(HttpMessageNotReadableException ex) {
        return new ErrorResponse("Invalid request body", "BAD_REQUEST", Instant.now());
    }

    /** @param ex persistence duplicate-key failure @return error body */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse duplicate(DuplicateKeyException ex) {
        return new ErrorResponse("Constraint violation", "CONFLICT", Instant.now());
    }
}
