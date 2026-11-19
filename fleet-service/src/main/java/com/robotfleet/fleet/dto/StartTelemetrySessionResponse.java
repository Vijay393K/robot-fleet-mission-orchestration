
package com.robotfleet.fleet.dto;

import java.util.UUID;

public record StartTelemetrySessionResponse(
        UUID robotId,
        UUID sessionId
) {
}
