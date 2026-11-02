
package com.robotfleet.robot_fleet.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.robotfleet.robot_fleet.RobotStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotTelemetry;
import com.robotfleet.robot_fleet.dto.TelemetryRequest;
import com.robotfleet.robot_fleet.repository.RobotRepository;
import com.robotfleet.robot_fleet.repository.RobotTelemetryRepository;

@Service
public class RobotTelemetryService {

    private final RobotRepository robotRepository;
    private final RobotTelemetryRepository telemetryRepository;

    public RobotTelemetryService(
            RobotRepository robotRepository,
            RobotTelemetryRepository telemetryRepository) {
        this.robotRepository = robotRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @Transactional
    public UUID startNewSession(UUID robotId) {

        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Robot not found: " + robotId));

        UUID sessionId = UUID.randomUUID();

        robot.setCurrentSessionId(sessionId);
        robotRepository.save(robot);

        return sessionId;
    }

    @Transactional
    public String ingestTelemetry(
            UUID robotId, TelemetryRequest request) {

        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Robot not found: " + robotId));

        UUID sessionId = request.sessionId();

      
        boolean duplicate =
                telemetryRepository
                    .existsByRobot_IdAndSessionIdAndSequenceNumber(
                        robotId,
                        sessionId,
                        request.sequenceNumber());

        if (duplicate) {
            return "Duplicate telemetry ignored.";
        }

     
        RobotTelemetry telemetry = new RobotTelemetry();
        telemetry.setRobot(robot);
        telemetry.setSessionId(sessionId);
        telemetry.setSequenceNumber(request.sequenceNumber());
        telemetry.setBatteryPercentage(request.batteryPercentage());
        telemetry.setLocationX(request.location().x());
        telemetry.setLocationY(request.location().y());
        telemetry.setSpeed(request.speed());
        telemetry.setHealthStatus(request.healthStatus().name());
        telemetry.setTimestamp(request.timestamp());
        telemetry.setReceivedAt(Instant.now());

        telemetryRepository.save(telemetry);

        if (sessionId.equals(robot.getCurrentSessionId())) {

            RobotTelemetry latest =
                    telemetryRepository
                        .findTopByRobot_IdAndSessionIdOrderBySequenceNumberDesc(
                            robotId, sessionId)
                        .orElse(null);

            if (latest != null &&
                    latest.getSequenceNumber()
                        == request.sequenceNumber()) {

            	robot.setBatteryPercentage(
            	        request.batteryPercentage());

            	robot.setLocationX(request.location().x());
            	robot.setLocationY(request.location().y());
            	robot.setLastHeartbeat(Instant.now());

       
            	if (robot.getStatus() == RobotStatus.OFFLINE) {
            	    robot.setStatus(RobotStatus.IDLE);
            	}

            	robotRepository.save(robot);
            }
        }

        return "Telemetry stored successfully.";
    }

    @Transactional(readOnly = true)
    public List<RobotTelemetry> getTelemetryHistory(
            UUID robotId, Instant start, Instant end) {

        if (start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "Start time must be before or equal to end time.");
        }

        if (!robotRepository.existsById(robotId)) {
            throw new IllegalArgumentException(
                    "Robot not found: " + robotId);
        }

        return telemetryRepository
                .findByRobot_IdAndTimestampBetweenOrderByTimestampAsc(
                        robotId, start, end);
    }
}
