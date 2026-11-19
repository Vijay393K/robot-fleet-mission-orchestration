
package com.robotfleet.fleet.service;

import java.time.Duration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.robotfleet.fleet.dto.RobotAvailabilityResponse.UnavailableReason;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.entity.RobotReservation;
import com.robotfleet.fleet.enums.ReservationStatus;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.repository.RobotRepository;
import com.robotfleet.fleet.repository.RobotReservationRepository;

@Service
public class RobotReservationService {

    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(2);

    private final RobotRepository robotRepository;
    private final RobotReservationRepository reservationRepository;
    private final RobotAvailabilityService availabilityService;

public RobotReservationService(
        RobotRepository robotRepository,
        RobotReservationRepository reservationRepository,
        RobotAvailabilityService availabilityService) {

    this.robotRepository = robotRepository;
    this.reservationRepository = reservationRepository;
    this.availabilityService = availabilityService;
}

   
    @Transactional
    public RobotReservation reserveRobot(
            UUID robotId,
            UUID missionId,
            String operationScope,
            String idempotencyKey,
            String requestHash) {

        if (robotId == null || missionId == null
                || isBlank(operationScope)
                || isBlank(idempotencyKey)
                || isBlank(requestHash)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reservation request contains missing fields");
        }

        var existingReservation =
                reservationRepository.findByOperationScopeAndIdempotencyKey(
                        operationScope, idempotencyKey);

        if (existingReservation.isPresent()) {
            RobotReservation existing = existingReservation.get();

            boolean sameRequest =
                    existing.getRequestHash().equals(requestHash)
                    && existing.getRobot().getId().equals(robotId)
                    && existing.getMissionId().equals(missionId);

            if (!sameRequest) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used for a different request");
            }

            return existing;
        }


Robot robot = robotRepository.findByIdForUpdate(robotId)
        .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Robot not found"));

var reservationAfterLock =
        reservationRepository.findByOperationScopeAndIdempotencyKey(
                operationScope, idempotencyKey);

if (reservationAfterLock.isPresent()) {
    RobotReservation existing = reservationAfterLock.get();

    boolean sameRequest =
            existing.getRequestHash().equals(requestHash)
            && existing.getRobot().getId().equals(robotId)
            && existing.getMissionId().equals(missionId);

    if (!sameRequest) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Idempotency key was already used for a different request");
    }

    return existing;
}

Instant now = Instant.now();



     List<ReservationStatus> activeStatuses = List.of(
             ReservationStatus.RESERVED,
             ReservationStatus.CONFIRMED);

     List<RobotReservation> activeReservations =
             reservationRepository.findByRobot_IdAndStatusIn(
                     robotId, activeStatuses);

     for (RobotReservation existing : activeReservations) {

         if (existing.getStatus() == ReservationStatus.RESERVED
                 && !existing.getExpiresAt().isAfter(now)) {

             existing.setStatus(ReservationStatus.EXPIRED);
             continue;
         }

         throw new ResponseStatusException(
                 HttpStatus.CONFLICT,
                 "Robot already has an active reservation");
     }

     reservationRepository.flush();

     List<RobotReservation> missionReservations =
             reservationRepository.findByMissionIdAndStatusIn(
                     missionId, activeStatuses);

     for (RobotReservation existing : missionReservations) {

         if (existing.getStatus() == ReservationStatus.RESERVED
                 && !existing.getExpiresAt().isAfter(now)) {

             existing.setStatus(ReservationStatus.EXPIRED);
             continue;
         }

         throw new ResponseStatusException(
                 HttpStatus.CONFLICT,
                 "Mission already has an active robot reservation");
     }

     reservationRepository.flush();


        validateRobotEligibility(robot, now);

        RobotReservation reservation = new RobotReservation();

        reservation.setRobot(robot);
        reservation.setMissionId(missionId);
        reservation.setOperationScope(operationScope);
        reservation.setIdempotencyKey(idempotencyKey);
        reservation.setRequestHash(requestHash);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setExpiresAt(now.plus(RESERVATION_TTL));

        return reservationRepository.save(reservation);
    }

    

@Transactional
public RobotReservation confirmReservation(UUID reservationId) {

    UUID robotId = reservationRepository
            .findRobotIdByReservationId(reservationId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Reservation not found"));

    Robot robot = robotRepository.findByIdForUpdate(robotId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Reserved robot not found"));

    RobotReservation reservation =
            reservationRepository.findByIdForUpdate(reservationId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Reservation not found"));

    if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
        return reservation;
    }

    if (reservation.getStatus() != ReservationStatus.RESERVED) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Reservation cannot be confirmed in its current state");
    }

    Instant now = Instant.now();

    if (reservation.getExpiresAt() == null
            || !reservation.getExpiresAt().isAfter(now)) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Reservation has expired");
    }

    validateRobotEligibility(robot, now);

    robot.setStatus(RobotStatus.BUSY);
    robot.setCurrentMissionId(reservation.getMissionId());

    reservation.setStatus(ReservationStatus.CONFIRMED);
    reservation.setConfirmedAt(now);

    return reservation;
}

   

@Transactional
public RobotReservation releaseReservation(UUID reservationId) {

    UUID robotId = reservationRepository
            .findRobotIdByReservationId(reservationId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Reservation not found"));

    Robot robot = robotRepository.findByIdForUpdate(robotId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Reserved robot not found"));

    RobotReservation reservation =
            reservationRepository.findByIdForUpdate(reservationId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Reservation not found"));

    if (reservation.getStatus() == ReservationStatus.RELEASED
            || reservation.getStatus() == ReservationStatus.EXPIRED) {
        return reservation;
    }

    Instant now = Instant.now();

    if (reservation.getStatus() == ReservationStatus.CONFIRMED) {

        if (!reservation.getMissionId().equals(
                robot.getCurrentMissionId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Confirmed reservation does not match the robot's current mission");
        }

        robot.setCurrentMissionId(null);

        Instant heartbeatCutoff =
                now.minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);

        boolean heartbeatFresh =
                robot.getLastHeartbeat() != null
                && !robot.getLastHeartbeat().isBefore(heartbeatCutoff)
                && !robot.getLastHeartbeat().isAfter(now);

        if (!robot.isSafetyHold()
                && robot.getStatus() == RobotStatus.BUSY
                && heartbeatFresh) {

            robot.setStatus(RobotStatus.IDLE);
        }
    }

    reservation.setStatus(ReservationStatus.RELEASED);
    reservation.setReleasedAt(now);

    return reservation;
}

    @Transactional
    public int expireReservations() {

        Instant now = Instant.now();

        List<RobotReservation> expiredReservations =
                reservationRepository.findExpiredReservationsForUpdate(
                        ReservationStatus.RESERVED, now);

        for (RobotReservation reservation : expiredReservations) {
            reservation.setStatus(ReservationStatus.EXPIRED);
        }

        return expiredReservations.size();
    }


private void validateRobotEligibility(Robot robot, Instant now) {

    List<UnavailableReason> reasons =
            availabilityService.evaluateRobotState(robot, now);

    if (!reasons.isEmpty()) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Robot is unavailable: " + reasons);
    }
}


    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
    
}
