
package com.robotfleet.fleet.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record StartTelemetrySessionRequest(

        @NotNull(message = "Session ID is required")
        UUID sessionId

) {
}
