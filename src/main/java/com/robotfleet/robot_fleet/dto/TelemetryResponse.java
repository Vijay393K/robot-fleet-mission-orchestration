
package com.robotfleet.robot_fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record TelemetryResponse(

        Long id,

        UUID sessionId,

        long sequenceNumber,

        int batteryPercentage,

        double locationX,

        double locationY,

        double speed,

        String healthStatus,

        Instant timestamp,

        Instant receivedAt

) {
}
