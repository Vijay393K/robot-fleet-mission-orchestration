
package com.robotfleet.mission.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record FleetRobotReservationRequest(

        @NotNull(message = "Robot ID is required")
        UUID robotId,

        @NotNull(message = "Mission ID is required")
        UUID missionId

) {
}
