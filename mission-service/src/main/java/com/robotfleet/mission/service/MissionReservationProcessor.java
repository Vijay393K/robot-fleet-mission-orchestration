
package com.robotfleet.mission.service;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotAvailabilityResponse;
import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.exception.FleetServiceException;
import com.robotfleet.mission.exception.FleetServiceException.FailureType;
import com.robotfleet.mission.repository.MissionRepository;
import com.robotfleet.mission.service.MissionAssignmentLeaseService.OperationClaim;
import com.robotfleet.mission.service.MissionAssignmentOutcomeService.ReserveOutcome;

@Service
public class MissionReservationProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(MissionReservationProcessor.class);

    private final MissionRepository missionRepository;
    private final FleetServiceClient fleetServiceClient;
    private final MissionAssignmentOutcomeService outcomeService;

    public MissionReservationProcessor(
            MissionRepository missionRepository,
            FleetServiceClient fleetServiceClient,
            MissionAssignmentOutcomeService outcomeService) {

        this.missionRepository = missionRepository;
        this.fleetServiceClient = fleetServiceClient;
        this.outcomeService = outcomeService;
    }

    public void processReserve(OperationClaim claim) {

        Objects.requireNonNull(
                claim,
                "Assignment operation claim is required"
        );

        if (claim.status() != AssignmentOperationStatus.RESERVE_PENDING) {
            throw new IllegalArgumentException(
                    "Only RESERVE_PENDING operations can be processed"
            );
        }

        Mission mission = missionRepository
                .findById(claim.missionId())
                .orElse(null);

        if (!isMissionEligible(mission, claim)) {

            log.warn(
                    "Reservation processing deferred. "
                    + "operationId={}, missionId={}, reason={}",
                    claim.operationId(),
                    claim.missionId(),
                    "Mission state or generation mismatch"
            );

            scheduleRetry(
                    claim,
                    "MISSION_STATE_MISMATCH",
                    "Mission is no longer in the expected state"
            );

            return;
        }

        try {
            FleetRobotAvailabilityResponse availability =
                    fleetServiceClient.getRobotAvailability(
                            claim.robotId()
                    );

            if (!claim.robotId().equals(availability.robotId())
                    || availability.robotType() == null) {

                scheduleRetry(
                        claim,
                        "INVALID_AVAILABILITY_RESPONSE",
                        "Fleet returned inconsistent robot information"
                );

                return;
            }

            if (availability.robotType()
                    != mission.getRequiredRobotType()) {

                boolean aborted =
                        outcomeService.abortRejectedReserve(claim);

                log.warn(
                        "Robot type mismatch. "
                        + "operationId={}, requiredType={}, actualType={}, aborted={}",
                        claim.operationId(),
                        mission.getRequiredRobotType(),
                        availability.robotType(),
                        aborted
                );

                return;
            }

            // Reuse the same key on every retry.
            String idempotencyKey =
                    claim.operationId().toString();

            FleetRobotReservationResponse response =
                    fleetServiceClient.reserveRobot(
                            claim.robotId(),
                            claim.missionId(),
                            idempotencyKey
                    );

            ReserveOutcome outcome =
                    outcomeService.recordReserveResponse(
                            claim,
                            response
                    );

            switch (outcome) {

                case RECORDED -> log.info(
                        "Fleet reservation recorded. "
                        + "operationId={}, reservationId={}",
                        claim.operationId(),
                        response.reservationId()
                );

                case RESERVATION_INACTIVE -> log.info(
                        "Fleet reservation is inactive. "
                        + "operationId={}, reservationStatus={}",
                        claim.operationId(),
                        response.status()
                );

                case STALE_CLAIM -> log.warn(
                        "Reservation response ignored due to stale lease. "
                        + "operationId={}",
                        claim.operationId()
                );
            }

        } catch (FleetServiceException ex) {

            handleFleetFailure(claim, ex);
        }
    }

    private boolean isMissionEligible(
            Mission mission,
            OperationClaim claim) {

        return mission != null
                && mission.getStatus() == MissionStatus.PENDING
                && mission.getAssignedRobotId() == null
                && mission.getAssignmentGeneration()
                        == claim.assignmentGeneration();
    }

    private void handleFleetFailure(
            OperationClaim claim,
            FleetServiceException exception) {

        if (isDefinitiveRejection(exception)) {

            boolean aborted =
                    outcomeService.abortRejectedReserve(claim);

            log.warn(
                    "Fleet rejected reservation. "
                    + "operationId={}, operation={}, status={}, aborted={}",
                    claim.operationId(),
                    exception.getOperation(),
                    exception.getHttpStatus(),
                    aborted
            );

            return;
        }

        scheduleRetry(
                claim,
                exception.getFailureType().name(),
                exception.getMessage()
        );

        log.warn(
                "Fleet request will be retried. "
                + "operationId={}, fleetOperation={}, "
                + "outcomeUnknown={}, attempt={}",
                claim.operationId(),
                exception.getOperation(),
                exception.isOutcomeUnknown(),
                claim.attemptCount()
        );
    }

    private boolean isDefinitiveRejection(
            FleetServiceException exception) {

        if (exception.isOutcomeUnknown()
                || exception.getFailureType()
                        != FailureType.REQUEST_REJECTED) {

            return false;
        }

        Integer status = exception.getHttpStatus();

        if (status == null) {
            return false;
        }

        return status == 400
                || status == 404
                || status == 409
                || status == 422;
    }

    private void scheduleRetry(
            OperationClaim claim,
            String errorCode,
            String errorMessage) {

        long delaySeconds = calculateRetryDelay(
                claim.attemptCount()
        );

        Instant retryAt = Instant.now()
                .plusSeconds(delaySeconds);

        boolean scheduled = outcomeService.scheduleRetry(
                claim,
                retryAt,
                errorCode,
                errorMessage
        );

        if (!scheduled) {
            log.warn(
                    "Retry update ignored due to stale lease. "
                    + "operationId={}",
                    claim.operationId()
            );
        }
    }

    private long calculateRetryDelay(long attemptCount) {

        long exponent = Math.min(
                Math.max(attemptCount - 1L, 0L),
                5L
        );

        long delay = 2L << (int) exponent;

        return Math.min(delay, 60L);
    }
}
