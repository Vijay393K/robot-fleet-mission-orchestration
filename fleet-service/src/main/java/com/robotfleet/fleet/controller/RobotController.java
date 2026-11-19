
package com.robotfleet.fleet.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.robotfleet.fleet.dto.RobotAvailabilityResponse;
import com.robotfleet.fleet.service.RobotAvailabilityService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.robotfleet.fleet.dto.HeartbeatRequest;
import com.robotfleet.fleet.dto.RobotRegistrationRequest;
import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.entity.RobotHeartbeat;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.enums.RobotType;
import com.robotfleet.fleet.repository.RobotHeartbeatRepository;
import com.robotfleet.fleet.service.HeartbeatService;
import com.robotfleet.fleet.service.RobotService;
import com.robotfleet.fleet.dto.RobotLatestStateResponse;
@RestController
@RequestMapping("/api/v1/robots")
public class RobotController {

    private final RobotService robotService;
    private final HeartbeatService heartbeatService;
    private final RobotHeartbeatRepository heartbeatRepository;
    private final RobotAvailabilityService availabilityService;
    

public RobotController(
        RobotService robotService,
        HeartbeatService heartbeatService,
        RobotHeartbeatRepository heartbeatRepository,
        RobotAvailabilityService availabilityService) {

    this.robotService = robotService;
    this.heartbeatService = heartbeatService;
    this.heartbeatRepository = heartbeatRepository;
    this.availabilityService = availabilityService;
}

    @PostMapping
    public ResponseEntity<Robot> registerRobot(
            @RequestBody RobotRegistrationRequest request) {

        Robot robot = robotService.registerRobot(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(robot);
    }

    @GetMapping("/{robotId}")
    public ResponseEntity<Robot> getRobotById(
            @PathVariable UUID robotId) {

        return ResponseEntity.ok(
                robotService.getRobotById(robotId));
    }

@GetMapping("/{robotId}/state")
public ResponseEntity<RobotLatestStateResponse> getLatestState(
        @PathVariable UUID robotId) {

    RobotLatestStateResponse response =
            robotService.getLatestState(robotId);

    return ResponseEntity.ok(response);
}

@GetMapping("/{robotId}/availability")
public ResponseEntity<RobotAvailabilityResponse> getRobotAvailability(
        @PathVariable UUID robotId) {

    return ResponseEntity.ok(
            availabilityService.getAvailability(robotId));
}


    @GetMapping
    public ResponseEntity<Page<Robot>> getRobots(
            @RequestParam(required = false) RobotType type,
            @RequestParam(required = false) RobotStatus status,
            @RequestParam(required = false) Integer minBattery,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be >= 0 and size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                robotService.getRobots(
                        type, status, minBattery, pageable));
    }

    @PostMapping("/{robotId}/heartbeat")
    public ResponseEntity<Map<String, Object>> receiveHeartbeat(
            @PathVariable UUID robotId,
            @Valid @RequestBody HeartbeatRequest request) {

        Robot robot = heartbeatService.processHeartbeat(
                robotId,
                request.sequenceNumber());

        return ResponseEntity.ok(Map.of(
                "message", "Heartbeat received successfully",
                "robotId", robot.getId(),
                "sequenceNumber", robot.getLastHeartbeatSequence(),
                "lastHeartbeat", robot.getLastHeartbeat(),
                "status", robot.getStatus()
        ));
    }

    @GetMapping("/{robotId}/heartbeats")
    public ResponseEntity<Map<String, Object>> getRecentHeartbeats(
            @PathVariable UUID robotId) {

        robotService.getRobotById(robotId);

        List<RobotHeartbeat> heartbeats =
                heartbeatRepository
                    .findTop20ByRobot_IdOrderByReceivedAtDesc(robotId);

        List<Map<String, Object>> history = heartbeats.stream()
                .map(heartbeat -> {
                    Map<String, Object> record =
                            new LinkedHashMap<>();

                    record.put("id", heartbeat.getId());
                    record.put("robotId", robotId);
                    record.put("sessionId", heartbeat.getSessionId());
                    record.put("sequenceNumber",
                            heartbeat.getSequenceNumber());
                    record.put("receivedAt",
                            heartbeat.getReceivedAt());

                    return record;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "robotId", robotId,
                "count", history.size(),
                "heartbeats", history
        ));
    }
}
