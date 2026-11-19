
package com.robotfleet.fleet.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public class ReserveRobotRequest {

    @NotNull(message = "Robot ID is required")
    private UUID robotId;

    @NotNull(message = "Mission ID is required")
    private UUID missionId;

    public ReserveRobotRequest() {
    }

    public UUID getRobotId() {
        return robotId;
    }

    public void setRobotId(UUID robotId) {
        this.robotId = robotId;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public void setMissionId(UUID missionId) {
        this.missionId = missionId;
    }
}
