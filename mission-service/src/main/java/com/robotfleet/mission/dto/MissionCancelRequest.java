
package com.robotfleet.mission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MissionCancelRequest(

        @NotBlank(message = "Actor is required")
        @Size(max = 100, message = "Actor must not exceed 100 characters")
        String actor,

        @NotBlank(message = "Cancellation reason is required")
        String reason

) {
}
