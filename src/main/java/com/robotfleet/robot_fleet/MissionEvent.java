package com.robotfleet.robot_fleet;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mission_events")
@org.hibernate.annotations.Immutable
public class MissionEvent {

    @Id
    private UUID id;

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private MissionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private MissionStatus newStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    private String actor;

    private String reason;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public void setMissionId(UUID missionId) {
        this.missionId = missionId;
    }

    public MissionStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(MissionStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public MissionStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(MissionStatus newStatus) {
        this.newStatus = newStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}