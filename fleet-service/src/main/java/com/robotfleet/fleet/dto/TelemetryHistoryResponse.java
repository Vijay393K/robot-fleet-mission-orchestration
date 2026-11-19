
package com.robotfleet.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record TelemetryHistoryResponse(
        Long id,
        UUID robotId,
        UUID sessionId,
        long sequenceNumber,
        int batteryPercentage,
        Location location,
        double speed,
        String healthStatus,
        Instant timestamp,
        Instant receivedAt
) {

    public record Location(
            double x,
            double y
    ) {
    }
}
