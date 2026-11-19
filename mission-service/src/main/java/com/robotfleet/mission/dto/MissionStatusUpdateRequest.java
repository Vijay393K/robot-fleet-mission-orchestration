
package com.robotfleet.mission.dto;

import com.robotfleet.mission.enums.MissionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MissionStatusUpdateRequest(

        @NotNull(message = "Mission status is required")
        MissionStatus status,

        @NotBlank(message = "Actor is required")
        @Size(max = 100, message = "Actor must not exceed 100 characters")
        String actor,

        @NotBlank(message = "Reason is required")
        String reason

) {
}
