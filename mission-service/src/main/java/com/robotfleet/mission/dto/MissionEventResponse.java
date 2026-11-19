
package com.robotfleet.mission.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.robotfleet.mission.entity.MissionEvent;
import com.robotfleet.mission.enums.MissionStatus;

public record MissionEventResponse(
        UUID id,
        UUID missionId,
        MissionStatus previousStatus,
        MissionStatus newStatus,
        long assignmentGeneration,
        Instant changedAt,
        String actor,
        String reason,
        String correlationId
) {

    public static MissionEventResponse from(MissionEvent event) {
        Objects.requireNonNull(event, "Mission event must not be null");

        return new MissionEventResponse(
                event.getId(),
                event.getMissionId(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getAssignmentGeneration(),
                event.getChangedAt(),
                event.getActor(),
                event.getReason(),
                event.getCorrelationId()
        );
    }
}
