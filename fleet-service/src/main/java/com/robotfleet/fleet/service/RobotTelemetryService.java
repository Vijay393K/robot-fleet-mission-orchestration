
package com.robotfleet.fleet.service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.robotfleet.fleet.dto.TelemetryHistoryResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.fleet.dto.TelemetryRequest;
import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.entity.RobotTelemetry;
import com.robotfleet.fleet.entity.RobotTelemetrySession;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.repository.RobotRepository;
import com.robotfleet.fleet.repository.RobotTelemetryRepository;
import com.robotfleet.fleet.repository.RobotTelemetrySessionRepository;

@Service
public class RobotTelemetryService {

    private static final double MAX_COORDINATE = 100_000.0;

    private final RobotRepository robotRepository;
    private final RobotTelemetryRepository telemetryRepository;
    private final RobotTelemetrySessionRepository sessionRepository;

    public RobotTelemetryService(
            RobotRepository robotRepository,
            RobotTelemetryRepository telemetryRepository,
            RobotTelemetrySessionRepository sessionRepository) {

        this.robotRepository = robotRepository;
        this.telemetryRepository = telemetryRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public IngestionResult ingestTelemetry(
            UUID robotId,
            TelemetryRequest request) {

        validateRequest(robotId, request);

        // Serialize telemetry and session updates across service instances.
        Robot robot = robotRepository.findByIdForUpdate(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found: " + robotId));

        UUID sessionId = request.sessionId();
        long sequenceNumber = request.sequenceNumber();

        RobotTelemetrySession session = sessionRepository
                .findBySessionIdAndRobot_Id(sessionId, robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Telemetry session not found for this robot"));

        boolean currentSession =
                sessionId.equals(robot.getCurrentSessionId());

        if (session.isActive() != currentSession) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot telemetry session state is inconsistent");
        }

        Instant timestamp = request.timestamp()
                .truncatedTo(ChronoUnit.MICROS);

        Optional<RobotTelemetry> existingTelemetry =
                telemetryRepository
                        .findByRobot_IdAndSessionIdAndSequenceNumber(
                                robotId,
                                sessionId,
                                sequenceNumber);

        if (existingTelemetry.isPresent()) {
            return handleDuplicate(
                    existingTelemetry.get(),
                    request,
                    timestamp);
        }

        boolean latestStateUpdated = currentSession
                && isNewerSequence(robotId, sessionId, sequenceNumber);

        RobotTelemetry telemetry = createTelemetry(
                robot,
                request,
                timestamp);

        RobotTelemetry savedTelemetry =
                telemetryRepository.save(telemetry);

        if (latestStateUpdated) {
            updateRobotState(robot, request);
        }

        return new IngestionResult(
                savedTelemetry.getId(),
                robotId,
                sessionId,
                sequenceNumber,
                false,
                latestStateUpdated,
                latestStateUpdated
                        ? "Telemetry stored; latest state updated"
                        : "Historical telemetry stored; latest state unchanged");
    }

@Transactional(readOnly = true)
public Page<TelemetryHistoryResponse> getTelemetryHistory(
        UUID robotId,
        Instant from,
        Instant to,
        int page,
        int size) {

    if (robotId == null || from == null || to == null) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Robot ID, start time and end time are required");
    }

    if (from.isAfter(to)) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Start time must not be after end time");
    }

    if (page < 0 || size < 1 || size > 100) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Page must be non-negative and size must be between 1 and 100");
    }

    Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                    Sort.Order.desc("timestamp"),
                    Sort.Order.desc("id")));

    Page<RobotTelemetry> telemetryPage =
            telemetryRepository.findByRobot_IdAndTimestampBetween(
                    robotId,
                    from,
                    to,
                    pageable);

    if (telemetryPage.isEmpty() && !robotRepository.existsById(robotId)) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Robot not found: " + robotId);
    }

    return telemetryPage.map(telemetry ->
            new TelemetryHistoryResponse(
                    telemetry.getId(),
                    robotId,
                    telemetry.getSessionId(),
                    telemetry.getSequenceNumber(),
                    telemetry.getBatteryPercentage(),
                    new TelemetryHistoryResponse.Location(
                            telemetry.getLocationX(),
                            telemetry.getLocationY()),
                    telemetry.getSpeed(),
                    telemetry.getHealthStatus(),
                    telemetry.getTimestamp(),
                    telemetry.getReceivedAt()));
}

    private IngestionResult handleDuplicate(
            RobotTelemetry existing,
            TelemetryRequest request,
            Instant timestamp) {

        if (!hasSamePayload(existing, request, timestamp)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Telemetry sequence already exists with a different payload");
        }

        return new IngestionResult(
                existing.getId(),
                existing.getRobot().getId(),
                existing.getSessionId(),
                existing.getSequenceNumber(),
                true,
                false,
                "Duplicate telemetry already stored");
    }

    private boolean isNewerSequence(
            UUID robotId,
            UUID sessionId,
            long sequenceNumber) {

        return telemetryRepository
                .findTopByRobot_IdAndSessionIdOrderBySequenceNumberDesc(
                        robotId,
                        sessionId)
                .map(telemetry ->
                        sequenceNumber > telemetry.getSequenceNumber())
                .orElse(true);
    }

    private RobotTelemetry createTelemetry(
            Robot robot,
            TelemetryRequest request,
            Instant timestamp) {

        RobotTelemetry telemetry = new RobotTelemetry();

        telemetry.setRobot(robot);
        telemetry.setSessionId(request.sessionId());
        telemetry.setSequenceNumber(request.sequenceNumber());
        telemetry.setBatteryPercentage(request.batteryPercentage());
        telemetry.setLocationX(request.location().x());
        telemetry.setLocationY(request.location().y());
        telemetry.setSpeed(request.speed());
        telemetry.setHealthStatus(request.healthStatus().name());
        telemetry.setTimestamp(timestamp);
        telemetry.setReceivedAt(Instant.now());

        return telemetry;
    }

    private void updateRobotState(
            Robot robot,
            TelemetryRequest request) {

        robot.setBatteryPercentage(request.batteryPercentage());
        robot.setLocationX(request.location().x());
        robot.setLocationY(request.location().y());

        if (request.healthStatus() == TelemetryRequest.HealthStatus.ERROR) {
            robot.setStatus(RobotStatus.ERROR);
            robot.setSafetyHold(true);
        }

        // Telemetry never refreshes heartbeat or clears a safety hold.
    }

    private boolean hasSamePayload(
            RobotTelemetry stored,
            TelemetryRequest request,
            Instant timestamp) {

        return stored.getBatteryPercentage() == request.batteryPercentage()
                && Double.compare(
                        stored.getLocationX(),
                        request.location().x()) == 0
                && Double.compare(
                        stored.getLocationY(),
                        request.location().y()) == 0
                && Double.compare(
                        stored.getSpeed(),
                        request.speed()) == 0
                && stored.getHealthStatus().equals(
                        request.healthStatus().name())
                && stored.getTimestamp().equals(timestamp);
    }

    private void validateRequest(
            UUID robotId,
            TelemetryRequest request) {

        if (robotId == null
                || request == null
                || request.sessionId() == null
                || request.sequenceNumber() == null
                || request.batteryPercentage() == null
                || request.location() == null
                || request.location().x() == null
                || request.location().y() == null
                || request.speed() == null
                || request.healthStatus() == null
                || request.timestamp() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Required telemetry fields are missing");
        }

        if (request.sequenceNumber() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Telemetry sequence must be positive");
        }

        int battery = request.batteryPercentage();

        if (battery < 0 || battery > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Battery percentage must be between 0 and 100");
        }

        double x = request.location().x();
        double y = request.location().y();
        double speed = request.speed();

        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || Math.abs(x) > MAX_COORDINATE
                || Math.abs(y) > MAX_COORDINATE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid telemetry coordinates");
        }

        if (!Double.isFinite(speed) || speed < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Telemetry speed must be finite and non-negative");
        }

        if (request.timestamp().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Telemetry timestamp cannot be in the future");
        }
    }

    public record IngestionResult(
            Long telemetryId,
            UUID robotId,
            UUID sessionId,
            long sequenceNumber,
            boolean duplicate,
            boolean latestStateUpdated,
            String message
    ) {
    }
}
