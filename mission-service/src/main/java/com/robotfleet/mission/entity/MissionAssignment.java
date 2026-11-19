
package com.robotfleet.mission.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "mission_assignments")
public class MissionAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mission_id", nullable = false, updatable = false)
    private UUID missionId;

    @Column(name = "robot_id", nullable = false, updatable = false)
    private UUID robotId;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "assignment_generation", nullable = false, updatable = false)
    private long assignmentGeneration;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", nullable = false, length = 100, updatable = false)
    private String assignedBy;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_reason", length = 255)
    private String endReason;

    protected MissionAssignment() {
    }

    public MissionAssignment(
            UUID missionId,
            UUID robotId,
            UUID reservationId,
            long assignmentGeneration,
            String assignedBy) {

        this.missionId = Objects.requireNonNull(missionId, "Mission ID is required");
        this.robotId = Objects.requireNonNull(robotId, "Robot ID is required");
        this.reservationId = Objects.requireNonNull(reservationId, "Reservation ID is required");

        if (assignmentGeneration <= 0) {
            throw new IllegalArgumentException("Assignment generation must be positive");
        }

        if (assignedBy == null || assignedBy.isBlank()) {
            throw new IllegalArgumentException("Assigned by is required");
        }

        if (assignedBy.length() > 100) {
            throw new IllegalArgumentException("Assigned by exceeds 100 characters");
        }

        this.assignmentGeneration = assignmentGeneration;
        this.assignedBy = assignedBy;
    }

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }

    public void close(String reason) {
        if (endedAt != null) {
            throw new IllegalStateException("Assignment is already closed");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("End reason is required");
        }

        if (reason.length() > 255) {
            throw new IllegalArgumentException("End reason exceeds 255 characters");
        }

        Instant now = Instant.now();

        if (assignedAt != null && now.isBefore(assignedAt)) {
            throw new IllegalStateException("End time cannot precede assignment time");
        }

        this.endedAt = now;
        this.endReason = reason;
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

    public UUID getReservationId() {
        return reservationId;
    }

    public long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getEndReason() {
        return endReason;
    }
}
