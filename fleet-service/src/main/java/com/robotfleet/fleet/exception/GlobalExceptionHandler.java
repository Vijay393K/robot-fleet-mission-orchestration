
package com.robotfleet.fleet.exception;

import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        int statusCode = ex.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(statusCode);

        String error = status != null
                ? status.getReasonPhrase()
                : "HTTP Error";

        String code = status != null
                ? status.name()
                : "HTTP_" + statusCode;

        String message = ex.getReason() != null
                ? ex.getReason()
                : error;

        return ResponseEntity.status(ex.getStatusCode())
                .body(buildError(
                        statusCode,
                        error,
                        code,
                        message,
                        request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> details = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(
                fieldError -> details.put(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()));

        Map<String, Object> body = buildError(
                400,
                "Bad Request",
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI());

        body.put("details", details);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return ResponseEntity.badRequest().body(
                buildError(
                        400,
                        "Bad Request",
                        "INVALID_ARGUMENT",
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        if (isUniqueConstraintViolation(ex)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(buildError(
                            409,
                            "Conflict",
                            "CONFLICT",
                            "Operation conflicts with an existing database record",
                            request.getRequestURI()));
        }

        log.error(
                "Database integrity violation at path {}",
                request.getRequestURI(),
                ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        500,
                        "Internal Server Error",
                        "DATABASE_ERROR",
                        "Database operation could not be completed",
                        request.getRequestURI()));
    }

    private boolean isUniqueConstraintViolation(Throwable exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof SQLException sqlException) {

                if (UNIQUE_VIOLATION_SQLSTATE.equals(
                        sqlException.getSQLState())) {
                    return true;
                }

                SQLException next = sqlException.getNextException();

                while (next != null) {
                    if (UNIQUE_VIOLATION_SQLSTATE.equals(
                            next.getSQLState())) {
                        return true;
                    }

                    next = next.getNextException();
                }
            }

            cause = cause.getCause();
        }

        return false;
    }

    private Map<String, Object> buildError(
            int status,
            String error,
            String code,
            String message,
            String path) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", Instant.now());
        body.put("status", status);
        body.put("error", error);
        body.put("code", code);
        body.put("message", message);
        body.put("path", path);

        return body;
    }
}
