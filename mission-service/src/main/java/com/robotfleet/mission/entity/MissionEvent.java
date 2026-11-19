
package com.robotfleet.mission.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.robotfleet.mission.enums.MissionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "mission_events")
public class MissionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mission_id", nullable = false, updatable = false)
    private UUID missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30, updatable = false)
    private MissionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30, updatable = false)
    private MissionStatus newStatus;

    @Column(name = "assignment_generation", nullable = false, updatable = false)
    private long assignmentGeneration;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "actor", nullable = false, length = 100, updatable = false)
    private String actor;

    @Column(name = "reason", nullable = false, columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    protected MissionEvent() {
    }

    public MissionEvent(
            UUID missionId,
            MissionStatus previousStatus,
            MissionStatus newStatus,
            long assignmentGeneration,
            String actor,
            String reason,
            String correlationId) {

        this.missionId = Objects.requireNonNull(missionId, "Mission ID is required");
        this.newStatus = Objects.requireNonNull(newStatus, "New status is required");

        if (previousStatus == null && newStatus != MissionStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Initial mission event must have PENDING status");
        }

        if (previousStatus != null && previousStatus == newStatus) {
            throw new IllegalArgumentException(
                    "Previous and new mission statuses must differ");
        }

        if (assignmentGeneration < 0) {
            throw new IllegalArgumentException(
                    "Assignment generation cannot be negative");
        }

        if (actor == null || actor.isBlank() || actor.length() > 100) {
            throw new IllegalArgumentException("Invalid event actor");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Event reason is required");
        }

        if (correlationId != null && correlationId.length() > 100) {
            throw new IllegalArgumentException("Correlation ID exceeds 100 characters");
        }

        this.previousStatus = previousStatus;
        this.assignmentGeneration = assignmentGeneration;
        this.actor = actor;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public MissionStatus getPreviousStatus() {
        return previousStatus;
    }

    public MissionStatus getNewStatus() {
        return newStatus;
    }

    public long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getActor() {
        return actor;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
