
package com.robotfleet.mission.service;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.exception.FleetServiceException;
import com.robotfleet.mission.service.MissionAssignmentLeaseService.OperationClaim;
import com.robotfleet.mission.service.MissionAssignmentOutcomeService.ReleaseOutcome;

@Service
public class MissionReleaseProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(MissionReleaseProcessor.class);

    private final FleetServiceClient fleetServiceClient;
    private final MissionAssignmentOutcomeService outcomeService;

    public MissionReleaseProcessor(
            FleetServiceClient fleetServiceClient,
            MissionAssignmentOutcomeService outcomeService) {

        this.fleetServiceClient = fleetServiceClient;
        this.outcomeService = outcomeService;
    }

    public void processRelease(OperationClaim claim) {

        Objects.requireNonNull(
                claim,
                "Assignment operation claim is required"
        );

        if (claim.status() != AssignmentOperationStatus.RELEASE_PENDING
                || claim.reservationId() == null) {

            throw new IllegalArgumentException(
                    "Operation is not awaiting reservation release"
            );
        }

        FleetRobotReservationResponse response;

        try {
            // This HTTP call is outside a database transaction.
            // Repeating release is safe for RELEASED or EXPIRED
            // reservations in the existing Fleet Service.
            response = fleetServiceClient.releaseReservation(
                    claim.reservationId()
            );

        } catch (FleetServiceException exception) {

            handleFleetFailure(claim, exception);
            return;
        }

        ReleaseOutcome outcome =
                outcomeService.recordReleaseResponse(
                        claim,
                        response
                );

        switch (outcome) {

            case COMPLETED -> log.info(
                    "Reservation compensation completed. "
                    + "operationId={}, reservationId={}, "
                    + "fleetStatus={}",
                    claim.operationId(),
                    claim.reservationId(),
                    response.status()
            );

            case STALE_CLAIM -> log.warn(
                    "Release response ignored because lease is stale. "
                    + "operationId={}",
                    claim.operationId()
            );

            case STILL_ACTIVE -> {

                log.warn(
                        "Fleet reservation remains active after release. "
                        + "operationId={}, fleetStatus={}",
                        claim.operationId(),
                        response.status()
                );

                scheduleRetry(
                        claim,
                        "RELEASE_NOT_COMPLETED",
                        "Fleet reservation is still active",
                        retryDelaySeconds(claim.attemptCount())
                );
            }

            case INVALID_RESPONSE -> {

                log.error(
                        "Fleet returned an inconsistent release response. "
                        + "operationId={}, reservationId={}",
                        claim.operationId(),
                        claim.reservationId()
                );

                scheduleRetry(
                        claim,
                        "INVALID_RELEASE_RESPONSE",
                        "Fleet release response does not match the operation",
                        retryDelaySeconds(claim.attemptCount())
                );
            }
        }
    }

    private void handleFleetFailure(
            OperationClaim claim,
            FleetServiceException exception) {

        boolean definitiveClientError =
                !exception.isOutcomeUnknown()
                && exception.getHttpStatus() != null
                && exception.getHttpStatus() >= 400
                && exception.getHttpStatus() < 500;

        // A definitive rejection may require reconciliation.
        // Never mark the reservation RELEASED based on an HTTP error.
        long delaySeconds = definitiveClientError
                ? 300L
                : retryDelaySeconds(claim.attemptCount());

        boolean scheduled = scheduleRetry(
                claim,
                exception.getFailureType().name(),
                exception.getMessage(),
                delaySeconds
        );

        if (definitiveClientError) {

            log.error(
                    "Fleet rejected reservation release. "
                    + "Manual reconciliation may be needed. "
                    + "operationId={}, reservationId={}, "
                    + "httpStatus={}, retryScheduled={}",
                    claim.operationId(),
                    claim.reservationId(),
                    exception.getHttpStatus(),
                    scheduled
            );

        } else {

            log.warn(
                    "Reservation release will be retried. "
                    + "operationId={}, reservationId={}, "
                    + "outcomeUnknown={}, retryScheduled={}",
                    claim.operationId(),
                    claim.reservationId(),
                    exception.isOutcomeUnknown(),
                    scheduled
            );
        }
    }

    private boolean scheduleRetry(
            OperationClaim claim,
            String errorCode,
            String errorMessage,
            long delaySeconds) {

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
                    "Release retry ignored because lease is stale. "
                    + "operationId={}",
                    claim.operationId()
            );
        }

        return scheduled;
    }

    private long retryDelaySeconds(long attemptCount) {

        long exponent = Math.min(
                Math.max(attemptCount, 1L),
                6L
        );

        return Math.min(
                1L << (int) exponent,
                60L
        );
    }
}
