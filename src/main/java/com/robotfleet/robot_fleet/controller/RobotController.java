package com.robotfleet.robot_fleet.controller;


import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotStatus;
import com.robotfleet.robot_fleet.RobotType;
import com.robotfleet.robot_fleet.repository.RobotRepository;
import com.robotfleet.robot_fleet.service.RobotService;

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


@RestController
public class RobotController {

    private final RobotRepository robotRepository;
    private final RobotService robotService;

    public RobotController(
            RobotRepository robotRepository,
            RobotService robotService) {
        this.robotRepository = robotRepository;
        this.robotService = robotService;
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
}