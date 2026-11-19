
package com.robotfleet.mission.dto;

import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.enums.RobotType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record MissionRequest(

        @NotNull(message = "Mission type is required")
        MissionType missionType,

        @NotNull(message = "Priority is required")
        MissionPriority priority,

        @NotNull(message = "Required robot type is required")
        RobotType requiredRobotType,

        @NotNull(message = "Pickup location is required")
        @Valid
        Location pickupLocation,

        @NotNull(message = "Drop location is required")
        @Valid
        Location dropLocation

) {

    public record Location(

            @NotNull(message = "X coordinate is required")
            @DecimalMin(value = "-100000.0", message = "X coordinate is below the minimum")
            @DecimalMax(value = "100000.0", message = "X coordinate exceeds the maximum")
            Double x,

            @NotNull(message = "Y coordinate is required")
            @DecimalMin(value = "-100000.0", message = "Y coordinate is below the minimum")
            @DecimalMax(value = "100000.0", message = "Y coordinate exceeds the maximum")
            Double y

    ) {
    }
}
