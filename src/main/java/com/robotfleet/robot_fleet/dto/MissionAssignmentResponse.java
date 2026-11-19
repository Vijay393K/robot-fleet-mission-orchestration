package com.robotfleet.robot_fleet.dto;

import java.time.Instant;
import java.util.UUID;

import com.robotfleet.robot_fleet.entity.MissionAssignment;

public record MissionAssignmentResponse(
        UUID id,
        UUID missionId,
        UUID robotId,
        long assignmentGeneration,
        Instant assignedAt,
        Instant endedAt,
        String assignedBy,
        String endReason
) {

    public MissionAssignmentResponse(MissionAssignment assignment) {
        this(
                assignment.getId(),
                assignment.getMission().getId(),
                assignment.getRobot().getId(),
                assignment.getAssignmentGeneration(),
                assignment.getAssignedAt(),
                assignment.getEndedAt(),
                assignment.getAssignedBy(),
                assignment.getEndReason()
        );
    }
}
