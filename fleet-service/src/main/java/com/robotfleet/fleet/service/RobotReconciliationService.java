
package com.robotfleet.fleet.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.enums.ReservationStatus;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.repository.RobotRepository;
import com.robotfleet.fleet.repository.RobotReservationRepository;

@Service
public class RobotReconciliationService {

    private static final Logger log =
            LoggerFactory.getLogger(RobotReconciliationService.class);

    private static final Duration HEARTBEAT_TIMEOUT =
            Duration.ofSeconds(30);

    private final RobotRepository robotRepository;
    private final RobotReservationRepository reservationRepository;

    public RobotReconciliationService(
            RobotRepository robotRepository,
            RobotReservationRepository reservationRepository) {

        this.robotRepository = robotRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public Robot reconcileRobot(
            UUID robotId,
            boolean physicallySafe,
            String operator) {

        if (robotId == null || operator == null || operator.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot ID and operator are required");
        }

        if (!physicallySafe) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Physical safety confirmation is required");
        }

        Robot robot = robotRepository.findByIdForUpdate(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found"));

        Instant now = Instant.now();
        Instant lastHeartbeat = robot.getLastHeartbeat();

        if (lastHeartbeat == null
                || lastHeartbeat.isBefore(now.minus(HEARTBEAT_TIMEOUT))
                || lastHeartbeat.isAfter(now)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A fresh robot heartbeat is required");
        }

        if (robot.getCurrentMissionId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot still has an active mission");
        }

        if (robot.getStatus() != RobotStatus.OFFLINE
                && robot.getStatus() != RobotStatus.BUSY
                && robot.getStatus() != RobotStatus.IDLE) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot status does not permit reconciliation");
        }

        var reservations = reservationRepository.findByRobot_IdAndStatusIn(
                robotId,
                List.of(
                        ReservationStatus.RESERVED,
                        ReservationStatus.CONFIRMED));

        boolean activeReservation = reservations.stream().anyMatch(
                reservation ->
                        reservation.getStatus() == ReservationStatus.CONFIRMED
                        || (reservation.getExpiresAt() != null
                                && reservation.getExpiresAt().isAfter(now)));

        if (activeReservation) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot has an active reservation");
        }

        robot.setSafetyHold(false);
        robot.setStatus(RobotStatus.IDLE);

        log.info(
                "Robot reconciled: robotId={}, operator={}, status={}",
                robotId,
                operator.trim(),
                robot.getStatus());

        return robot;
    }
}
