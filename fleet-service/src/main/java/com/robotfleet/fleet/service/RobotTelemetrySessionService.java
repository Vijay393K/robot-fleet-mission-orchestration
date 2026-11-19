
package com.robotfleet.fleet.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.entity.RobotTelemetrySession;
import com.robotfleet.fleet.repository.RobotRepository;
import com.robotfleet.fleet.repository.RobotTelemetrySessionRepository;

@Service
public class RobotTelemetrySessionService {

    private final RobotRepository robotRepository;
    private final RobotTelemetrySessionRepository sessionRepository;

    public RobotTelemetrySessionService(
            RobotRepository robotRepository,
            RobotTelemetrySessionRepository sessionRepository) {

        this.robotRepository = robotRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public UUID startSession(UUID robotId, UUID sessionId) {

        if (sessionId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Session ID is required");
        }

        Robot robot = robotRepository.findByIdForUpdate(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found: " + robotId));


        Optional<RobotTelemetrySession> existingSession =
                sessionRepository.findById(sessionId);

        if (existingSession.isPresent()) {

            RobotTelemetrySession existing = existingSession.get();

            boolean sameRobot =
                    existing.getRobot().getId().equals(robotId);

            boolean currentSession =
                    sessionId.equals(robot.getCurrentSessionId());

            if (sameRobot && currentSession && existing.isActive()) {
                return sessionId;
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Session ID is already used or no longer active");
        }

        Instant now = Instant.now();

        Optional<RobotTelemetrySession> activeSession =
                sessionRepository.findByRobot_IdAndEndedAtIsNull(robotId);

        if (activeSession.isPresent()) {

            activeSession.get().close(now);

            
            sessionRepository.flush();
        }

        RobotTelemetrySession newSession =
                new RobotTelemetrySession(
                        sessionId,
                        robot,
                        now);

        sessionRepository.save(newSession);

        
        robot.setCurrentSessionId(sessionId);

        return sessionId;
    }
}
