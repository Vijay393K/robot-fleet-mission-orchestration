package com.robotfleet.robot_fleet.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotStatus;
import com.robotfleet.robot_fleet.repository.RobotRepository;

@Service
public class HeartbeatService {

    private final RobotRepository robotRepository;

    public HeartbeatService(RobotRepository robotRepository) {
        this.robotRepository = robotRepository;
    }

    @Transactional
    public Robot processHeartbeat(UUID robotId, long sequenceNumber) {

        if (sequenceNumber <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Heartbeat sequence number must be greater than zero");
        }

        Robot robot = robotRepository.findByIdForHeartbeat(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found"));

        if (sequenceNumber <= robot.getLastHeartbeatSequence()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Duplicate or outdated heartbeat sequence number");
        }

        robot.setLastHeartbeatSequence(sequenceNumber);
        robot.setLastHeartbeat(Instant.now());

        // Keep the robot offline until safety reconciliation is completed.
        if (robot.isSafetyHold()) {
            robot.setStatus(RobotStatus.OFFLINE);
        }

        return robotRepository.save(robot);
    }
}
