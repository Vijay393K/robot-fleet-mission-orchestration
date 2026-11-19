
package com.robotfleet.mission.entity;

import java.time.Instant;
import java.util.UUID;

import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.enums.RobotType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "missions")
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_type", nullable = false, length = 30)
    private MissionType missionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private MissionPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_robot_type", nullable = false, length = 20)
    private RobotType requiredRobotType;

    @Column(name = "pickup_x", nullable = false)
    private double pickupX;

    @Column(name = "pickup_y", nullable = false)
    private double pickupY;

    @Column(name = "drop_x", nullable = false)
    private double dropX;

    @Column(name = "drop_y", nullable = false)
    private double dropY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MissionStatus status = MissionStatus.PENDING;

    @Column(name = "assigned_robot_id")
    private UUID assignedRobotId;

    @Column(name = "assignment_generation", nullable = false)
    private long assignmentGeneration = 0;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Mission() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public MissionType getMissionType() {
        return missionType;
    }

    public void setMissionType(MissionType missionType) {
        this.missionType = missionType;
    }

    public MissionPriority getPriority() {
        return priority;
    }

    public void setPriority(MissionPriority priority) {
        this.priority = priority;
    }

    public RobotType getRequiredRobotType() {
        return requiredRobotType;
    }

    public void setRequiredRobotType(RobotType requiredRobotType) {
        this.requiredRobotType = requiredRobotType;
    }

    public double getPickupX() {
        return pickupX;
    }

    public void setPickupX(double pickupX) {
        this.pickupX = pickupX;
    }

    public double getPickupY() {
        return pickupY;
    }

    public void setPickupY(double pickupY) {
        this.pickupY = pickupY;
    }

    public double getDropX() {
        return dropX;
    }

    public void setDropX(double dropX) {
        this.dropX = dropX;
    }

    public double getDropY() {
        return dropY;
    }

    public void setDropY(double dropY) {
        this.dropY = dropY;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public UUID getAssignedRobotId() {
        return assignedRobotId;
    }

    public void setAssignedRobotId(UUID assignedRobotId) {
        this.assignedRobotId = assignedRobotId;
    }

    public long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    public void setAssignmentGeneration(long assignmentGeneration) {
        this.assignmentGeneration = assignmentGeneration;
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
}
