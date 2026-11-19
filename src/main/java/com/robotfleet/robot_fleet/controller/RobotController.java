package com.robotfleet.robot_fleet.controller;


import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.service.MissionAssignmentService;
import com.robotfleet.robot_fleet.dto.MissionAssignmentResponse;

import com.robotfleet.robot_fleet.RobotStatus;
import com.robotfleet.robot_fleet.RobotType;
import com.robotfleet.robot_fleet.repository.RobotRepository;
import com.robotfleet.robot_fleet.service.RobotService;


import com.robotfleet.robot_fleet.dto.HeartbeatRequest;
import com.robotfleet.robot_fleet.service.HeartbeatService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.robot_fleet.dto.RobotReconciliationRequest;
import com.robotfleet.robot_fleet.service.RobotReconciliationService;


import com.robotfleet.robot_fleet.RobotHeartbeat;
import com.robotfleet.robot_fleet.repository.RobotHeartbeatRepository;
import java.util.LinkedHashMap;

@RestController
public class RobotController {

    private final RobotRepository robotRepository;
    private final RobotService robotService;
    private final HeartbeatService heartbeatService;
    private final RobotReconciliationService reconciliationService;
    private final RobotHeartbeatRepository heartbeatRepository;
    private final MissionAssignmentService missionAssignmentService;
    
    
    public RobotController(
            RobotRepository robotRepository,
            RobotService robotService,
            HeartbeatService heartbeatService,
            RobotReconciliationService reconciliationService,
            RobotHeartbeatRepository heartbeatRepository,
            MissionAssignmentService missionAssignmentService) {

        this.robotRepository = robotRepository;
        this.robotService = robotService;
        this.heartbeatService = heartbeatService;
        this.reconciliationService = reconciliationService;
        this.heartbeatRepository = heartbeatRepository;
        this.missionAssignmentService = missionAssignmentService;
    }
    @GetMapping("/")
    public String home() {
        return "Robot fleet mission orchestration is running.";
    }

    @GetMapping("/api/robots")
    public Page<Robot> getRobots(
            @RequestParam(required = false) RobotType type,
            @RequestParam(required = false) RobotStatus status,
            @RequestParam(required = false) Integer minBattery,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (minBattery != null && (minBattery < 0 || minBattery > 100)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum battery must be between 0 and 100");
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be at least 0 and size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(
                page, size, Sort.by("name").ascending());

        return robotRepository.searchRobots(
                type, status, minBattery, pageable);
    }

    @GetMapping("/api/robots/{id:[0-9a-fA-F\\-]{36}}")
    public Robot getRobotById(@PathVariable UUID id) {
        return robotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Robot not found"));
    }

    @PostMapping("/api/robots")
    public ResponseEntity<Robot> registerRobot(@RequestBody Robot robot) {

        if (robot.getName() == null || robot.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Robot name is required");
        }

        if (robot.getType() == null) { throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Robot type is required");
        }

        if (robot.getBatteryPercentage() < 0 || robot.getBatteryPercentage() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Battery percentage must be between 0 and 100");
        }

        robot.setId(UUID.randomUUID());

        if (robot.getStatus() == null) {
            robot.setStatus(RobotStatus.IDLE);
        }

        robot.setLastHeartbeat(Instant.now());

        Robot savedRobot = robotRepository.save(robot);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedRobot);
    }
    @GetMapping("/api/v1/robots/{robotId}/assignments")
    public ResponseEntity<List<MissionAssignmentResponse>> getRobotAssignmentHistory(
            @PathVariable UUID robotId) {

        return ResponseEntity.ok(
                missionAssignmentService.getRobotAssignmentHistory(robotId)
        );
    }
    
    @PutMapping("/api/robots/{id}/status")
    public Robot updateRobotStatus(
            @PathVariable UUID id,
            @RequestParam RobotStatus status) {

        Robot robot = robotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Robot not found"));

        robot.setStatus(status);

        return robotRepository.save(robot);
    }

    @GetMapping("/api/robots/{id}/availability")
    public Map<String, Object> checkAvailability(@PathVariable UUID id) {

        Robot robot = robotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Robot not found"));

        boolean available = robotService.isRobotAvailable(robot);

        return Map.of(
                "robotId", robot.getId(),
                "robotName", robot.getName(),
                "available", available
        );
    }
    @GetMapping("/api/robots/inactive")
    public List<Robot> getInactiveRobots() {
        return robotService.getInactiveRobots();
    }
    @PostMapping("/api/v1/robots/{robotId}/heartbeat")
    public Map<String, Object> receiveHeartbeat(
            @PathVariable UUID robotId,
            @RequestBody HeartbeatRequest request) {

        Robot robot = heartbeatService.processHeartbeat(
                robotId,
                request.sequenceNumber());

        return Map.of(
                "message", "Heartbeat received successfully",
                "robotId", robot.getId(),
                "sequenceNumber", robot.getLastHeartbeatSequence(),
                "lastHeartbeat", robot.getLastHeartbeat(),
                "status", robot.getStatus()
        );
    }
    
    
    @GetMapping("/api/v1/robots/{robotId}/heartbeats")
    public Map<String, Object> getRecentHeartbeats(
            @PathVariable UUID robotId) {

        robotRepository.findById(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found"));

        List<RobotHeartbeat> heartbeats =
                heartbeatRepository.findTop20ByRobot_IdOrderByReceivedAtDesc(
                        robotId);

        List<Map<String, Object>> history = heartbeats.stream()
                .map(heartbeat -> {
                    Map<String, Object> record = new LinkedHashMap<>();

                    record.put("id", heartbeat.getId());
                    record.put("robotId", robotId);
                    record.put("sessionId", heartbeat.getSessionId());
                    record.put("sequenceNumber", heartbeat.getSequenceNumber());
                    record.put("receivedAt", heartbeat.getReceivedAt());

                    return record;
                })
                .toList();

        return Map.of(
                "robotId", robotId,
                "count", history.size(),
                "heartbeats", history
        );
    }
    
    
    @PostMapping("/api/v1/robots/{robotId}/reconcile")
    public Map<String, Object> reconcileRobot(
            @PathVariable UUID robotId,
            @RequestBody RobotReconciliationRequest request) {

        Robot robot = reconciliationService.reconcileRobot(
                robotId, request);

        return Map.of(
                "message", "Robot safety reconciliation completed",
                "robotId", robot.getId(),
                "status", robot.getStatus(),
                "safetyHold", robot.isSafetyHold()
        );
    }
}