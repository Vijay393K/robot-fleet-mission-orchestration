
package com.robotfleet.fleet.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.fleet.dto.StartTelemetrySessionRequest;
import com.robotfleet.fleet.dto.StartTelemetrySessionResponse;
import com.robotfleet.fleet.service.RobotTelemetrySessionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/robots/{robotId}/telemetry-sessions")
public class RobotTelemetrySessionController {

    private final RobotTelemetrySessionService sessionService;

    public RobotTelemetrySessionController(
            RobotTelemetrySessionService sessionService) {

        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<StartTelemetrySessionResponse> startSession(
            @PathVariable UUID robotId,
            @Valid @RequestBody StartTelemetrySessionRequest request) {

        UUID sessionId = sessionService.startSession(
                robotId,
                request.sessionId());

        return ResponseEntity.ok(
                new StartTelemetrySessionResponse(
                        robotId,
                        sessionId));
    }
}
