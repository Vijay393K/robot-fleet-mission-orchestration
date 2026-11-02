package com.robotfleet.robot_fleet.dto;

import com.robotfleet.robot_fleet.MissionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MissionStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private MissionStatus status;

    @NotBlank(message = "Actor is required")
    private String actor;

    private String reason;

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
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