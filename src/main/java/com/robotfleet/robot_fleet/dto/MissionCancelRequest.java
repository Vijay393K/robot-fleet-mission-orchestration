package com.robotfleet.robot_fleet.dto;

import jakarta.validation.constraints.NotBlank;

public class MissionCancelRequest {

    @NotBlank(message = "Actor is required")
    private String actor;

    private String reason;

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