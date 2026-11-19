
package com.robotfleet.robot_fleet.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotHeartbeat;
import com.robotfleet.robot_fleet.RobotStatus;
import com.robotfleet.robot_fleet.repository.RobotHeartbeatRepository;
import com.robotfleet.robot_fleet.repository.RobotRepository;

@Service
public class HeartbeatService {

    private final RobotRepository robotRepository;
    private final RobotHeartbeatRepository heartbeatRepository;

    public HeartbeatService(
            RobotRepository robotRepository,
            RobotHeartbeatRepository heartbeatRepository) {
        this.robotRepository = robotRepository;
        this.heartbeatRepository = heartbeatRepository;
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

        Instant receivedAt = Instant.now();

        robot.setLastHeartbeatSequence(sequenceNumber);
        robot.setLastHeartbeat(receivedAt);

        if (robot.isSafetyHold()) {
            robot.setStatus(RobotStatus.OFFLINE);
        }

        RobotHeartbeat heartbeat = new RobotHeartbeat();
        heartbeat.setRobot(robot);
        heartbeat.setSessionId(robot.getCurrentSessionId());
        heartbeat.setSequenceNumber(sequenceNumber);
        heartbeat.setReceivedAt(receivedAt);

        heartbeatRepository.save(heartbeat);

        return robotRepository.save(robot);
    }
}
