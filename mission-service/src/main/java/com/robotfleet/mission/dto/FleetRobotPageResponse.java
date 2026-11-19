
package com.robotfleet.mission.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.robotfleet.mission.enums.RobotType;

public record FleetRobotPageResponse(
        List<RobotSummary> content,
        int number,
        int size,
        long totalElements,
        int totalPages
) {

    public record RobotSummary(
            UUID id,
            String name,
            RobotType type,
            String status,
            int batteryPercentage,
            double locationX,
            double locationY,
            Instant lastHeartbeat,
            UUID currentMissionId,
            boolean safetyHold
    ) {
    }
}
