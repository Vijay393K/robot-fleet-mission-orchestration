
package com.robotfleet.mission.exception;

import java.util.Objects;

public final class FleetServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum FailureType {
        REQUEST_REJECTED,
        REMOTE_SERVICE_ERROR,
        CONNECTION_FAILURE,
        INVALID_RESPONSE
    }

    private final String operation;
    private final FailureType failureType;
    private final Integer httpStatus;
    private final boolean outcomeUnknown;

    public FleetServiceException(
            String operation,
            FailureType failureType,
            Integer httpStatus,
            boolean outcomeUnknown,
            String message,
            Throwable cause) {

        super(message, cause);

        this.operation = Objects.requireNonNull(
                operation, "Operation is required");

        this.failureType = Objects.requireNonNull(
                failureType, "Failure type is required");

        this.httpStatus = httpStatus;
        this.outcomeUnknown = outcomeUnknown;
    }

    public String getOperation() {
        return operation;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
