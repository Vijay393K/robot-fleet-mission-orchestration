
package com.robotfleet.mission.dto;

import java.util.List;
import java.util.UUID;

import com.robotfleet.mission.enums.RobotType;

public record FleetRobotAvailabilityResponse(
        UUID robotId,
        RobotType robotType,
        boolean available,
        int batteryPercentage,
        Location location,
        List<String> reasons
) {

    public record Location(
            double x,
            double y
    ) {
    }
}
