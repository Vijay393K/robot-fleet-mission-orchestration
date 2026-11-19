
package com.robotfleet.mission.exception;

import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.exception.FleetServiceException.FailureType;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNIQUE_VIOLATION = "23505";

    @ExceptionHandler(FleetServiceException.class)
    public ResponseEntity<Map<String, Object>> handleFleetServiceException(
            FleetServiceException ex,
            HttpServletRequest request) {

        HttpStatus status;
        String code;
        String message;

        if (ex.isOutcomeUnknown()) {

            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "FLEET_OPERATION_UNCERTAIN";
            message = "Fleet Service operation could not be confirmed";

        } else if (ex.getFailureType()
                == FailureType.REQUEST_REJECTED) {

            status = switch (ex.getHttpStatus() == null
                    ? 0 : ex.getHttpStatus()) {
                case 400 -> HttpStatus.BAD_REQUEST;
                case 404 -> HttpStatus.NOT_FOUND;
                case 409 -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_GATEWAY;
            };

            code = "FLEET_REQUEST_REJECTED";
            message = "Fleet Service rejected the request";

        } else if (ex.getFailureType()
                == FailureType.INVALID_RESPONSE) {

            status = HttpStatus.BAD_GATEWAY;
            code = "FLEET_INVALID_RESPONSE";
            message = "Fleet Service returned an invalid response";

        } else {

            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "FLEET_UNAVAILABLE";
            message = "Fleet Service is unavailable";
        }

        log.warn(
                "Fleet integration failed. operation={}, "
                + "failureType={}, remoteStatus={}, outcomeUnknown={}",
                ex.getOperation(),
                ex.getFailureType(),
                ex.getHttpStatus(),
                ex.isOutcomeUnknown()
        );

        return error(status, code, message, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        int statusCode = ex.getStatusCode().value();

        HttpStatus status = HttpStatus.resolve(statusCode);

        String message = ex.getReason() != null
                ? ex.getReason()
                : "Request could not be completed";

        return ResponseEntity.status(ex.getStatusCode()).body(
                errorBody(
                        statusCode,
                        status != null
                                ? status.getReasonPhrase()
                                : "HTTP Error",
                        status != null
                                ? status.name()
                                : "HTTP_ERROR",
                        message,
                        request.getRequestURI()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, Object> body = errorBody(
                400,
                "Bad Request",
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI()
        );

        Map<String, String> details = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(
                fieldError -> details.putIfAbsent(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                )
        );

        body.put("details", details);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Invalid value for parameter: " + ex.getName(),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body is missing or malformed",
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {

        return error(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "Resource was modified by another transaction",
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        if (isUniqueViolation(ex)) {
            return error(
                    HttpStatus.CONFLICT,
                    "DATABASE_CONFLICT",
                    "Operation conflicts with an existing record",
                    request
            );
        }

        log.error("Database integrity error", ex);

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "Database operation could not be completed",
                request
        );
    }

@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException ex,
        HttpServletRequest request) {

    return error(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            "HTTP method is not supported for this endpoint",
            request
    );
}

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Unexpected request failure. path={}",
                request.getRequestURI(),
                ex
        );

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request
        );
    }

    private static boolean isUniqueViolation(Throwable throwable) {

        for (Throwable cause = throwable;
                cause != null;
                cause = cause.getCause()) {

            if (cause instanceof SQLException sqlException) {

                for (SQLException current = sqlException;
                        current != null;
                        current = current.getNextException()) {

                    if (UNIQUE_VIOLATION.equals(
                            current.getSQLState())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {

        return ResponseEntity.status(status).body(
                errorBody(
                        status.value(),
                        status.getReasonPhrase(),
                        code,
                        message,
                        request.getRequestURI()
                )
        );
    }

    private Map<String, Object> errorBody(
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
