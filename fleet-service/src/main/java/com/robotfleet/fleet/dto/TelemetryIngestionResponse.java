
package com.robotfleet.fleet.dto;

import java.util.UUID;

public record TelemetryIngestionResponse(
        Long telemetryId,
        UUID robotId,
        UUID sessionId,
        long sequenceNumber,
        boolean duplicate,
        boolean latestStateUpdated,
        String message
) {
}