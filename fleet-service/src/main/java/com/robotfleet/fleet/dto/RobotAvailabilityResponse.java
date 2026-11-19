
package com.robotfleet.fleet.dto;

import java.util.List;
import java.util.UUID;

import com.robotfleet.fleet.enums.RobotType;

public record RobotAvailabilityResponse(
        UUID robotId,
        RobotType robotType,
        boolean available,
        int batteryPercentage,
        Location location,
        List<UnavailableReason> reasons
) {

    public record Location(double x, double y) {
    }

    public enum UnavailableReason {
        NOT_IDLE,
        LOW_BATTERY,
        STALE_HEARTBEAT,
        SAFETY_HOLD,
        ACTIVE_MISSION,
        ACTIVE_RESERVATION
    }
}
