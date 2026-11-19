
package com.robotfleet.fleet.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.fleet.dto.RobotAvailabilityResponse;
import com.robotfleet.fleet.dto.RobotAvailabilityResponse.UnavailableReason;
import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.entity.RobotReservation;
import com.robotfleet.fleet.enums.ReservationStatus;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.repository.RobotRepository;
import com.robotfleet.fleet.repository.RobotReservationRepository;

@Service
public class RobotAvailabilityService {

    private static final int MINIMUM_BATTERY = 25;
    private static final Duration HEARTBEAT_TIMEOUT =
            Duration.ofSeconds(30);

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(
                    ReservationStatus.RESERVED,
                    ReservationStatus.CONFIRMED);

    private final RobotRepository robotRepository;
    private final RobotReservationRepository reservationRepository;

    public RobotAvailabilityService(
            RobotRepository robotRepository,
            RobotReservationRepository reservationRepository) {

        this.robotRepository = robotRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public RobotAvailabilityResponse getAvailability(UUID robotId) {

        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found: " + robotId));

        Instant now = Instant.now();

        List<UnavailableReason> reasons =
                new ArrayList<>(evaluateRobotState(robot, now));

        List<RobotReservation> reservations =
                reservationRepository.findByRobot_IdAndStatusIn(
                        robotId, ACTIVE_STATUSES);

        boolean hasActiveReservation = reservations.stream()
                .anyMatch(reservation ->
                        reservation.getStatus() == ReservationStatus.CONFIRMED
                        || reservation.getExpiresAt().isAfter(now));

        if (hasActiveReservation) {
            reasons.add(UnavailableReason.ACTIVE_RESERVATION);
        }

        return new RobotAvailabilityResponse(
                robot.getId(),
                robot.getType(),
                reasons.isEmpty(),
                robot.getBatteryPercentage(),
                new RobotAvailabilityResponse.Location(
                        robot.getLocationX(),
                        robot.getLocationY()),
                List.copyOf(reasons));
    }

    public List<UnavailableReason> evaluateRobotState(
            Robot robot,
            Instant now) {

        List<UnavailableReason> reasons = new ArrayList<>();

        if (robot.getStatus() != RobotStatus.IDLE) {
            reasons.add(UnavailableReason.NOT_IDLE);
        }

        if (robot.getBatteryPercentage() < MINIMUM_BATTERY) {
            reasons.add(UnavailableReason.LOW_BATTERY);
        }

        Instant lastHeartbeat = robot.getLastHeartbeat();

        if (lastHeartbeat == null
                || lastHeartbeat.isAfter(now)
                || lastHeartbeat.isBefore(now.minus(HEARTBEAT_TIMEOUT))) {

            reasons.add(UnavailableReason.STALE_HEARTBEAT);
        }

        if (robot.isSafetyHold()) {
            reasons.add(UnavailableReason.SAFETY_HOLD);
        }

        if (robot.getCurrentMissionId() != null) {
            reasons.add(UnavailableReason.ACTIVE_MISSION);
        }

        return List.copyOf(reasons);
    }
}
