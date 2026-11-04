package com.robotfleet.robot_fleet.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class MissionCompleteRequest {

    @NotNull
    private UUID robotId;

    @Positive
    private long assignmentGeneration;

    @NotBlank
    private String actor;

    @NotBlank
    private String reason;

    public UUID getRobotId() {
        return robotId;
    }

    public void setRobotId(UUID robotId) {
        this.robotId = robotId;
    }

    public long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    public void setAssignmentGeneration(long assignmentGeneration) {
        this.assignmentGeneration = assignmentGeneration;
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