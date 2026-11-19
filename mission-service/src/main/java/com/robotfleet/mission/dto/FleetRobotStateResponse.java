
package com.robotfleet.mission.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FleetRobotStateResponse(
        UUID id,
        String status,
        UUID currentMissionId,
        boolean safetyHold,
        Instant lastHeartbeat
) {
}
