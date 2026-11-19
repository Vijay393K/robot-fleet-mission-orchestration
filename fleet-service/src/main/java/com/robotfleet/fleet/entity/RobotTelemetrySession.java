
package com.robotfleet.fleet.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "robot_telemetry_sessions")
public class RobotTelemetrySession {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", nullable = false, updatable = false)
    private Robot robot;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected RobotTelemetrySession() {
        // Required by JPA.
    }

    public RobotTelemetrySession(
            UUID sessionId,
            Robot robot,
            Instant startedAt) {

        this.sessionId = sessionId;
        this.robot = robot;
        this.startedAt = startedAt;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Robot getRobot() {
        return robot;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public void close(Instant endedAt) {

        if (endedAt == null) {
            throw new IllegalArgumentException(
                    "Session end time is required");
        }

        if (this.endedAt != null) {
            throw new IllegalStateException(
                    "Telemetry session is already closed");
        }

        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Session cannot end before it starts");
        }

        this.endedAt = endedAt;
    }
}
