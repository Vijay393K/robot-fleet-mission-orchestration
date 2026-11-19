
package com.robotfleet.mission.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.robotfleet.mission.enums.AssignmentOperationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "mission_assignment_operations")
public class MissionAssignmentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mission_id", nullable = false, updatable = false)
    private UUID missionId;

    @Column(name = "robot_id", nullable = false, updatable = false)
    private UUID robotId;

    @Column(name = "assignment_generation", nullable = false, updatable = false)
    private long assignmentGeneration;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AssignmentOperationStatus status;

    @Column(name = "reservation_id", unique = true)
    private UUID reservationId;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Column(name = "requested_by", nullable = false, length = 100, updatable = false)
    private String requestedBy;

    @Column(name = "reason", nullable = false, columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    @Column(name = "attempt_count", nullable = false)
    private long attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected MissionAssignmentOperation() {
    }

    public MissionAssignmentOperation(
            UUID missionId,
            UUID robotId,
            long assignmentGeneration,
            String requestedBy,
            String reason,
            String correlationId) {

        this.missionId = Objects.requireNonNull(missionId, "Mission ID is required");
        this.robotId = Objects.requireNonNull(robotId, "Robot ID is required");

        if (assignmentGeneration <= 0) {
            throw new IllegalArgumentException(
                    "Assignment generation must be positive");
        }

        if (requestedBy == null || requestedBy.isBlank()
                || requestedBy.length() > 100) {
            throw new IllegalArgumentException("Invalid requesting actor");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Operation reason is required");
        }

        if (correlationId != null && correlationId.length() > 100) {
            throw new IllegalArgumentException("Correlation ID exceeds 100 characters");
        }

        Instant now = Instant.now();

        this.assignmentGeneration = assignmentGeneration;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.correlationId = correlationId;

        this.status = AssignmentOperationStatus.RESERVE_PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void reservationSucceeded(UUID reservationId, Instant expiresAt) {
        if (status != AssignmentOperationStatus.RESERVE_PENDING) {
            throw new IllegalStateException("Operation is not awaiting reservation");
        }

        this.reservationId = Objects.requireNonNull(
                reservationId, "Reservation ID is required");
        this.reservationExpiresAt = Objects.requireNonNull(
                expiresAt, "Reservation expiry is required");

        this.status = AssignmentOperationStatus.CONFIRM_PENDING;
        this.nextAttemptAt = Instant.now();
        clearLease();
        clearError();
    }

    public void markConfirmed() {
        if (status != AssignmentOperationStatus.CONFIRM_PENDING) {
            throw new IllegalStateException("Operation is not awaiting confirmation");
        }

        status = AssignmentOperationStatus.CONFIRMED;
        nextAttemptAt = null;
        clearLease();
        clearError();
    }

    public void requestRelease() {
        if (reservationId == null) {
            throw new IllegalStateException("No reservation is available to release");
        }

        if (status != AssignmentOperationStatus.CONFIRM_PENDING
                && status != AssignmentOperationStatus.CONFIRMED) {
            throw new IllegalStateException("Operation cannot enter release state");
        }

        status = AssignmentOperationStatus.RELEASE_PENDING;
        nextAttemptAt = Instant.now();
        clearLease();
    }

    public void markReleased() {
        if (status != AssignmentOperationStatus.RELEASE_PENDING) {
            throw new IllegalStateException("Operation is not awaiting release");
        }

        status = AssignmentOperationStatus.RELEASED;
        nextAttemptAt = null;
        finishedAt = Instant.now();
        clearLease();
        clearError();
    }

    public void abort() {
        if (status != AssignmentOperationStatus.RESERVE_PENDING
                || reservationId != null) {
            throw new IllegalStateException(
                    "Operation cannot be aborted before reservation uncertainty is resolved");
        }

        status = AssignmentOperationStatus.ABORTED;
        nextAttemptAt = null;
        finishedAt = Instant.now();
        clearLease();
    }

    public void recordAttempt(Instant attemptedAt) {
        if (!isPending()) {
            throw new IllegalStateException("Operation is not pending");
        }

        attemptCount = Math.addExact(attemptCount, 1);
        lastAttemptAt = Objects.requireNonNull(attemptedAt);
    }

    public void scheduleRetry(
            Instant retryAt,
            String errorCode,
            String errorMessage) {

        if (!isPending()) {
            throw new IllegalStateException("Operation is not pending");
        }

        nextAttemptAt = Objects.requireNonNull(retryAt);
        lastErrorCode = truncate(errorCode, 100);
        lastErrorMessage = truncate(errorMessage, 1000);
        clearLease();
    }

    public void claimLease(UUID token, Instant expiresAt) {
        if (!isPending()) {
            throw new IllegalStateException("Only pending operations can be leased");
        }

        leaseToken = Objects.requireNonNull(token);
        leaseExpiresAt = Objects.requireNonNull(expiresAt);
    }

    public void clearLease() {
        leaseToken = null;
        leaseExpiresAt = null;
    }

    private void clearError() {
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    private boolean isPending() {
        return status == AssignmentOperationStatus.RESERVE_PENDING
                || status == AssignmentOperationStatus.CONFIRM_PENDING
                || status == AssignmentOperationStatus.RELEASE_PENDING;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public UUID getRobotId() {
        return robotId;
    }

    public long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    public AssignmentOperationStatus getStatus() {
        return status;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public Instant getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public long getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public UUID getLeaseToken() {
        return leaseToken;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
