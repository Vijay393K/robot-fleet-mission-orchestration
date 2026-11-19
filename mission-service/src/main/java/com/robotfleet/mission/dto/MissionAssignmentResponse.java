
package com.robotfleet.mission.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.robotfleet.mission.entity.MissionAssignment;

public record MissionAssignmentResponse(
        UUID id,
        UUID missionId,
        UUID robotId,
        UUID reservationId,
        long assignmentGeneration,
        Instant assignedAt,
        String assignedBy,
        Instant endedAt,
        String endReason
) {

    public static MissionAssignmentResponse from(
            MissionAssignment assignment) {

        Objects.requireNonNull(
                assignment,
                "Mission assignment must not be null"
        );

        return new MissionAssignmentResponse(
                assignment.getId(),
                assignment.getMissionId(),
                assignment.getRobotId(),
                assignment.getReservationId(),
                assignment.getAssignmentGeneration(),
                assignment.getAssignedAt(),
                assignment.getAssignedBy(),
                assignment.getEndedAt(),
                assignment.getEndReason()
        );
    }
}
