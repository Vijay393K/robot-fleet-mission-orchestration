
package com.robotfleet.mission.dto;

import java.time.Instant;
import java.util.UUID;

public record FleetRobotReservationResponse(
        UUID reservationId,
        UUID robotId,
        UUID missionId,
        ReservationStatus status,
        Instant reservedAt,
        Instant expiresAt,
        Instant confirmedAt,
        Instant releasedAt
) {

    public enum ReservationStatus {
        RESERVED,
        CONFIRMED,
        RELEASED,
        EXPIRED
    }
}
