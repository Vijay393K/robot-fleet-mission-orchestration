
package com.robotfleet.robot_fleet.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;

public record TelemetryRequest(

    @NotNull
    UUID sessionId,

    @PositiveOrZero
    long sequenceNumber,

    @Min(0)
    @Max(100)
    int batteryPercentage,

    @NotNull
    @Valid
    Location location,

    @DecimalMin("0.0")
    double speed,

    @NotNull
    HealthStatus healthStatus,

    @NotNull
    @PastOrPresent
    Instant timestamp

) {
    public record Location(

        @DecimalMin("-100000.0")
        @DecimalMax("100000.0")
        double x,

        @DecimalMin("-100000.0")
        @DecimalMax("100000.0")
        double y

    ) {}

    public enum HealthStatus {
        HEALTHY,
        WARNING,
        ERROR
    }
}
