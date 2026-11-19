
package com.robotfleet.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HeartbeatRequest(

    @NotNull(message = "Sequence number is required")
    @Positive(message = "Sequence number must be positive")
    Long sequenceNumber

) {
}
