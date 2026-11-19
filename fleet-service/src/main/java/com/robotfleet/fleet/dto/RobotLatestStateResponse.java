
package com.robotfleet.fleet.dto;

import java.time.Instant;
import java.util.UUID;

import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.enums.RobotType;

public record RobotLatestStateResponse(
        UUID robotId,
        RobotType type,
        RobotStatus status,
        int batteryPercentage,
        Location location,
        Instant lastHeartbeat,
        boolean heartbeatFresh,
        boolean safetyHold,
        UUID currentMissionId,
        UUID currentSessionId
) {

    public record Location(
            double x,
            double y
    ) {
    }
}
