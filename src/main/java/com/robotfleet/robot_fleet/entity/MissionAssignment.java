
package com.robotfleet.robot_fleet.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.Robot;

@Entity
@Table(name = "mission_assignments")
public class MissionAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "mission_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_assignments_mission")
    )
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "robot_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_assignments_robot")
    )
    private Robot robot;

    @Column(name = "assignment_generation", nullable = false)
    private long assignmentGeneration;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "assigned_by", nullable = false, length = 100)
    private String assignedBy;

    @Column(name = "end_reason", length = 255)
    private String endReason;

    protected MissionAssignment() {
    }

    public MissionAssignment(
            Mission mission,
            Robot robot,
            long assignmentGeneration,
            Instant assignedAt,
            String assignedBy) {

        this.mission = mission;
        this.robot = robot;
        this.assignmentGeneration = assignmentGeneration;
        this.assignedAt = assignedAt;
        this.assignedBy = assignedBy;
    }

    public UUID getId() {
        return id;
    }

    public Mission getMission() {
        return mission;
    }

    public Robot getRobot() {
        return robot;
    }

    public long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public String getEndReason() {
        return endReason;
    }

    public void endAssignment(Instant endedAt, String endReason) {
        if (this.endedAt != null) {
            throw new IllegalStateException("Assignment has already ended");
        }

        if (endedAt == null || endReason == null || endReason.isBlank()) {
            throw new IllegalArgumentException(
                "End time and end reason are required"
            );
        }

        this.endedAt = endedAt;
        this.endReason = endReason;
    }
}
