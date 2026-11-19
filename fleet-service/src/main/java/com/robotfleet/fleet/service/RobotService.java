
package com.robotfleet.fleet.service;

import java.util.UUID;

import java.time.Duration;
import java.time.Instant;

import com.robotfleet.fleet.dto.RobotLatestStateResponse;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.fleet.dto.RobotRegistrationRequest;
import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.enums.RobotType;
import com.robotfleet.fleet.repository.RobotRepository;

@Service
public class RobotService {

    private final RobotRepository robotRepository;
    private static final Duration HEARTBEAT_TIMEOUT =
            Duration.ofSeconds(30);

    public RobotService(RobotRepository robotRepository) {
        this.robotRepository = robotRepository;
    }

    @Transactional
    public Robot registerRobot(RobotRegistrationRequest request) {

        Robot robot = new Robot();

        robot.setId(UUID.randomUUID());
        robot.setName(request.getName());
        robot.setType(request.getType());
        robot.setBatteryPercentage(request.getBatteryPercentage());
        robot.setLocationX(request.getLocationX());
        robot.setLocationY(request.getLocationY());
        robot.setStatus(RobotStatus.IDLE);
        robot.setLastHeartbeat(null);
        robot.setCurrentMissionId(null);
        robot.setCurrentSessionId(null);
        robot.setLastHeartbeatSequence(0);
        robot.setSafetyHold(false);

        return robotRepository.save(robot);
    }

    @Transactional(readOnly = true)
    public Robot getRobotById(UUID robotId) {

        return robotRepository.findById(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found: " + robotId));
    }

@Transactional(readOnly = true)
public RobotLatestStateResponse getLatestState(UUID robotId) {

    Robot robot = getRobotById(robotId);

    Instant now = Instant.now();
    Instant lastHeartbeat = robot.getLastHeartbeat();

    boolean heartbeatFresh = lastHeartbeat != null
            && !lastHeartbeat.isAfter(now)
            && !lastHeartbeat.isBefore(now.minus(HEARTBEAT_TIMEOUT));

    return new RobotLatestStateResponse(
            robot.getId(),
            robot.getType(),
            robot.getStatus(),
            robot.getBatteryPercentage(),
            new RobotLatestStateResponse.Location(
                    robot.getLocationX(),
                    robot.getLocationY()),
            lastHeartbeat,
            heartbeatFresh,
            robot.isSafetyHold(),
            robot.getCurrentMissionId(),
            robot.getCurrentSessionId());
}

    @Transactional(readOnly = true)
    public Page<Robot> getRobots(
            RobotType type,
            RobotStatus status,
            Integer minBattery,
            Pageable pageable) {

        return robotRepository.searchRobots(
                type,
                status,
                minBattery,
                pageable);
    }
}
