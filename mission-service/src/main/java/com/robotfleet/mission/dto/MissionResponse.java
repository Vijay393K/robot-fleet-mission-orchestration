
package com.robotfleet.mission.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.enums.RobotType;

public record MissionResponse(
        UUID id,
        MissionType missionType,
        MissionPriority priority,
        RobotType requiredRobotType,
        Location pickupLocation,
        Location dropLocation,
        MissionStatus status,
        UUID assignedRobotId,
        long assignmentGeneration,
        Instant createdAt,
        Instant updatedAt
) {

    public record Location(double x, double y) {
    }

    public static MissionResponse from(Mission mission) {
        Objects.requireNonNull(mission, "Mission must not be null");

        return new MissionResponse(
                mission.getId(),
                mission.getMissionType(),
                mission.getPriority(),
                mission.getRequiredRobotType(),
                new Location(
                        mission.getPickupX(),
                        mission.getPickupY()
                ),
                new Location(
                        mission.getDropX(),
                        mission.getDropY()
                ),
                mission.getStatus(),
                mission.getAssignedRobotId(),
                mission.getAssignmentGeneration(),
                mission.getCreatedAt(),
                mission.getUpdatedAt()
        );
    }
}
