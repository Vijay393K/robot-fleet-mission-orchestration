package com.robotfleet.robot_fleet.dto;

import java.time.Instant;
import java.util.UUID;

import com.robotfleet.robot_fleet.MissionEvent;
import com.robotfleet.robot_fleet.MissionStatus;

public class MissionEventResponse {

    private UUID id;
    private UUID missionId;
    private MissionStatus previousStatus;
    private MissionStatus newStatus;
    private Instant changedAt;
    private String actor;
    private String reason;

    public MissionEventResponse(MissionEvent event) {
        this.id = event.getId();
        this.missionId = event.getMissionId();
        this.previousStatus = event.getPreviousStatus();
        this.newStatus = event.getNewStatus();
        this.changedAt = event.getChangedAt();
        this.actor = event.getActor();
        this.reason = event.getReason();
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

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getActor() {
        return actor;
    }

    public String getReason() {
        return reason;
    }
}