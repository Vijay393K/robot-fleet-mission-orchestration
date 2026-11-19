
package com.robotfleet.mission.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MissionAssignmentRequest(

        @NotNull(message = "Robot ID is required")
        UUID robotId,

        @NotBlank(message = "Actor is required")
        @Size(max = 100, message = "Actor must not exceed 100 characters")
        String actor,

        @NotBlank(message = "Reason is required")
        String reason

) {
}
