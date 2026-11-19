
package com.robotfleet.fleet.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record TelemetryRequest(

        @NotNull
        UUID sessionId,

        @NotNull
        @Positive
        Long sequenceNumber,

        @NotNull
        @Min(0)
        @Max(100)
        Integer batteryPercentage,

        @NotNull
        @Valid
        Location location,

        @NotNull
        @PositiveOrZero
        Double speed,

        @NotNull
        HealthStatus healthStatus,

        @NotNull
        @PastOrPresent
        Instant timestamp

) {

    public record Location(

            @NotNull
            @DecimalMin("-100000.0")
            @DecimalMax("100000.0")
            Double x,

            @NotNull
            @DecimalMin("-100000.0")
            @DecimalMax("100000.0")
            Double y

    ) {
    }

    public enum HealthStatus {
        HEALTHY,
        WARNING,
        ERROR
    }
}
