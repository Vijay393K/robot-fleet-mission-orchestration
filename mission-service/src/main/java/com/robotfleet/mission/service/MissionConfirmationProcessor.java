
package com.robotfleet.mission.service;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.exception.FleetServiceException;
import com.robotfleet.mission.exception.FleetServiceException.FailureType;
import com.robotfleet.mission.service.MissionAssignmentFinalizationService.FinalizationResult;
import com.robotfleet.mission.service.MissionAssignmentLeaseService.OperationClaim;

import com.robotfleet.mission.enums.AssignmentOperationStatus;

@Service
public class MissionConfirmationProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(MissionConfirmationProcessor.class);

    private final FleetServiceClient fleetServiceClient;
    private final MissionAssignmentFinalizationService finalizationService;
    private final MissionAssignmentOutcomeService outcomeService;

    public MissionConfirmationProcessor(
            FleetServiceClient fleetServiceClient,
            MissionAssignmentFinalizationService finalizationService,
            MissionAssignmentOutcomeService outcomeService) {

        this.fleetServiceClient = fleetServiceClient;
        this.finalizationService = finalizationService;
        this.outcomeService = outcomeService;
    }

    public void processConfirm(OperationClaim claim) {

        Objects.requireNonNull(
                claim,
                "Assignment operation claim is required"
        );

        if (claim.status() != AssignmentOperationStatus.CONFIRM_PENDING
                || claim.reservationId() == null) {

            throw new IllegalArgumentException(
                    "Operation is not awaiting reservation confirmation"
            );
        }

        FleetRobotReservationResponse response;

        try {
            // Fleet confirmation is idempotent for an
            // already confirmed reservation.
            response = fleetServiceClient.confirmReservation(
                    claim.reservationId()
            );

        } catch (FleetServiceException ex) {
            handleFleetFailure(claim, ex);
            return;
        }

        if (response.status() == null) {

            scheduleRetry(
                    claim,
                    "INVALID_CONFIRM_RESPONSE",
                    "Fleet returned a reservation without a status"
            );

            return;
        }

        switch (response.status()) {

            case CONFIRMED -> finalizeAssignment(claim, response);

            case RESERVED -> scheduleRetry(
                    claim,
                    "CONFIRMATION_NOT_COMPLETED",
                    "Fleet reservation is not yet confirmed"
            );

            case EXPIRED, RELEASED -> requestCompensation(
                    claim,
                    "Fleet reservation is no longer active"
            );
        }
    }

    private void finalizeAssignment(
            OperationClaim claim,
            FleetRobotReservationResponse response) {

        // This method starts a separate, local DB transaction.
        // There is no Fleet HTTP call inside that transaction.
        FinalizationResult result =
                finalizationService.finalizeConfirmedReservation(
                        claim,
                        response
                );

        switch (result) {

            case ASSIGNED -> log.info(
                    "Mission assignment finalized. "
                    + "missionId={}, robotId={}, "
                    + "reservationId={}, generation={}",
                    claim.missionId(),
                    claim.robotId(),
                    claim.reservationId(),
                    claim.assignmentGeneration()
            );

            case RELEASE_REQUIRED -> log.warn(
                    "Assignment cannot be finalized; "
                    + "reservation compensation required. "
                    + "operationId={}",
                    claim.operationId()
            );

            case STALE_CLAIM -> log.warn(
                    "Confirmation result ignored due to stale lease. "
                    + "operationId={}",
                    claim.operationId()
            );
        }
    }

    private void handleFleetFailure(
            OperationClaim claim,
            FleetServiceException exception) {

        if (isDefinitiveRejection(exception)) {

            requestCompensation(
                    claim,
                    "Fleet rejected reservation confirmation"
            );

            return;
        }

        scheduleRetry(
                claim,
                exception.getFailureType().name(),
                exception.getMessage()
        );

        log.warn(
                "Fleet confirmation will be retried. "
                + "operationId={}, remoteStatus={}, "
                + "outcomeUnknown={}",
                claim.operationId(),
                exception.getHttpStatus(),
                exception.isOutcomeUnknown()
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

        return status != null
                && (status == 400
                        || status == 404
                        || status == 409
                        || status == 422);
    }

    private void requestCompensation(
            OperationClaim claim,
            String reason) {

        boolean recorded =
                outcomeService.requestReleaseAfterConfirmationFailure(
                        claim
                );

        if (recorded) {
            log.warn(
                    "Reservation release scheduled. "
                    + "operationId={}, reservationId={}, reason={}",
                    claim.operationId(),
                    claim.reservationId(),
                    reason
            );
        } else {
            log.warn(
                    "Compensation update skipped due to stale lease. "
                    + "operationId={}",
                    claim.operationId()
            );
        }
    }

    private void scheduleRetry(
            OperationClaim claim,
            String errorCode,
            String errorMessage) {

        long exponent = Math.min(
                Math.max(claim.attemptCount(), 1L),
                5L
        );

        long delaySeconds = Math.min(
                1L << (int) exponent,
                60L
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
                    "Confirmation retry ignored due to stale lease. "
                    + "operationId={}",
                    claim.operationId()
            );
        }
    }
}
