
package com.robotfleet.robot_fleet.controller;

import java.util.UUID;

import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.robot_fleet.dto.TelemetryRequest;
import com.robotfleet.robot_fleet.service.RobotTelemetryService;

import jakarta.validation.Valid;
import com.robotfleet.robot_fleet.dto.TelemetryResponse;
import java.util.stream.Collectors;


import org.springframework.web.server.ResponseStatusException;

@RestController
public class RobotTelemetryController {

    private final RobotTelemetryService telemetryService;

    public RobotTelemetryController(
            RobotTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping("/api/robots/{robotId}/telemetry")
    public ResponseEntity<String> ingestTelemetry(
            @PathVariable UUID robotId,
            @Valid @RequestBody TelemetryRequest request) {

        String result = telemetryService.ingestTelemetry(
                robotId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }
    


@GetMapping("/api/robots/{robotId}/telemetry")
public List<TelemetryResponse> getTelemetryHistory(
        @PathVariable UUID robotId,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant start,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant end) {

  
    if (start.isAfter(end)) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Start time must be before or equal to end time"
        );
    }

    return telemetryService
            .getTelemetryHistory(robotId, start, end)
            .stream()
            .map(telemetry -> new TelemetryResponse(
                    telemetry.getId(),
                    telemetry.getSessionId(),
                    telemetry.getSequenceNumber(),
                    telemetry.getBatteryPercentage(),
                    telemetry.getLocationX(),
                    telemetry.getLocationY(),
                    telemetry.getSpeed(),
                    telemetry.getHealthStatus(),
                    telemetry.getTimestamp(),
                    telemetry.getReceivedAt()
            ))
            .collect(Collectors.toList());
}
@PostMapping("/api/robots/{robotId}/sessions")
public ResponseEntity<java.util.Map<String, UUID>> startNewSession(
        @PathVariable UUID robotId) {

    UUID sessionId = telemetryService.startNewSession(robotId);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(java.util.Map.of("sessionId", sessionId));
}

}
