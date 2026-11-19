
package com.robotfleet.mission.service;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.service.MissionAssignmentLeaseService.OperationClaim;

@Service
public class MissionAssignmentOutcomeService {

    public enum ReserveOutcome {
        RECORDED,
        RESERVATION_INACTIVE,
        STALE_CLAIM
    }

    private final MissionAssignmentOperationRepository operationRepository;

    public MissionAssignmentOutcomeService(
            MissionAssignmentOperationRepository operationRepository) {

        this.operationRepository = operationRepository;
    }

    @Transactional
    public ReserveOutcome recordReserveResponse(
            OperationClaim claim,
            FleetRobotReservationResponse response) {

        Objects.requireNonNull(claim, "Operation claim is required");
        Objects.requireNonNull(response, "Fleet response is required");

        if (claim.status() != AssignmentOperationStatus.RESERVE_PENDING) {
            throw new IllegalArgumentException(
                    "Claim is not awaiting reservation"
            );
        }

        MissionAssignmentOperation operation = operationRepository
                .findByIdForUpdate(claim.operationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Assignment operation not found"
                ));

        if (!ownsActiveLease(operation, claim)) {
            return ReserveOutcome.STALE_CLAIM;
        }

        if (!Objects.equals(response.robotId(), claim.robotId())
                || !Objects.equals(response.missionId(), claim.missionId())
                || response.reservationId() == null
                || response.status() == null) {

            throw new IllegalStateException(
                    "Fleet reservation response does not match the operation"
            );
        }

        switch (response.status()) {

            case RESERVED, CONFIRMED -> {

                Instant expiresAt = response.expiresAt();

                if (expiresAt == null) {
                    throw new IllegalStateException(
                            "Fleet reservation expiry is missing"
                    );
                }

                if (response.status()
                        == FleetRobotReservationResponse.ReservationStatus.RESERVED
                        && !expiresAt.isAfter(Instant.now())) {

                    operation.abort();
                    return ReserveOutcome.RESERVATION_INACTIVE;
                }

                operation.reservationSucceeded(
                        response.reservationId(),
                        expiresAt
                );

                return ReserveOutcome.RECORDED;
            }

            case EXPIRED, RELEASED -> {
                operation.abort();
                return ReserveOutcome.RESERVATION_INACTIVE;
            }
        }

        throw new IllegalStateException(
                "Unsupported Fleet reservation status"
        );
    }

    @Transactional
    public boolean scheduleRetry(
            OperationClaim claim,
            Instant retryAt,
            String errorCode,
            String errorMessage) {

        Objects.requireNonNull(claim, "Operation claim is required");
        Objects.requireNonNull(retryAt, "Retry time is required");

        MissionAssignmentOperation operation = operationRepository
                .findByIdForUpdate(claim.operationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Assignment operation not found"
                ));

        if (!ownsActiveLease(operation, claim)) {
            return false;
        }

        operation.scheduleRetry(
                retryAt,
                errorCode,
                errorMessage
        );

        return true;
    }

    @Transactional
    public boolean abortRejectedReserve(OperationClaim claim) {

        Objects.requireNonNull(claim, "Operation claim is required");

        if (claim.status() != AssignmentOperationStatus.RESERVE_PENDING) {
            throw new IllegalArgumentException(
                    "Only a pending reservation can be aborted"
            );
        }

        MissionAssignmentOperation operation = operationRepository
                .findByIdForUpdate(claim.operationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Assignment operation not found"
                ));

        if (!ownsActiveLease(operation, claim)) {
            return false;
        }

        operation.abort();
        return true;
    }

@Transactional
public boolean requestReleaseAfterConfirmationFailure(
        OperationClaim claim) {

    Objects.requireNonNull(
            claim,
            "Operation claim is required"
    );

    if (claim.status() != AssignmentOperationStatus.CONFIRM_PENDING
            || claim.reservationId() == null) {

        throw new IllegalArgumentException(
                "Operation is not awaiting reservation confirmation"
        );
    }

    MissionAssignmentOperation operation =
            operationRepository.findByIdForUpdate(
                    claim.operationId()
            ).orElseThrow(() -> new IllegalStateException(
                    "Assignment operation not found"
            ));

    if (!ownsActiveLease(operation, claim)) {
        return false;
    }

    operation.requestRelease();

    return true;
}

public enum ReleaseOutcome {
    COMPLETED,
    STILL_ACTIVE,
    INVALID_RESPONSE,
    STALE_CLAIM
}

@Transactional
public ReleaseOutcome recordReleaseResponse(
        OperationClaim claim,
        FleetRobotReservationResponse response) {

    Objects.requireNonNull(
            claim,
            "Operation claim is required"
    );

    Objects.requireNonNull(
            response,
            "Fleet release response is required"
    );

    if (claim.status()
            != AssignmentOperationStatus.RELEASE_PENDING
            || claim.reservationId() == null) {

        throw new IllegalArgumentException(
                "Operation is not awaiting reservation release"
        );
    }

    MissionAssignmentOperation operation =
            operationRepository.findByIdForUpdate(
                    claim.operationId()
            ).orElseThrow(() -> new IllegalStateException(
                    "Assignment operation not found"
            ));

    if (!ownsActiveLease(operation, claim)) {
        return ReleaseOutcome.STALE_CLAIM;
    }

    boolean matchingReservation =
            Objects.equals(
                    operation.getReservationId(),
                    claim.reservationId()
            )
            && Objects.equals(
                    response.reservationId(),
                    claim.reservationId()
            )
            && Objects.equals(
                    response.robotId(),
                    claim.robotId()
            )
            && Objects.equals(
                    response.missionId(),
                    claim.missionId()
            );

    if (!matchingReservation || response.status() == null) {
        return ReleaseOutcome.INVALID_RESPONSE;
    }

    switch (response.status()) {

        case RELEASED, EXPIRED -> {

            operation.markReleased();
            return ReleaseOutcome.COMPLETED;
        }

        case RESERVED, CONFIRMED -> {

            return ReleaseOutcome.STILL_ACTIVE;
        }
    }

    return ReleaseOutcome.INVALID_RESPONSE;
}

    private boolean ownsActiveLease(
            MissionAssignmentOperation operation,
            OperationClaim claim) {

        return operation.getId().equals(claim.operationId())
                && operation.getMissionId().equals(claim.missionId())
                && operation.getRobotId().equals(claim.robotId())
                && operation.getAssignmentGeneration()
                        == claim.assignmentGeneration()
                && operation.getStatus() == claim.status()
                && Objects.equals(
                        operation.getLeaseToken(),
                        claim.leaseToken()
                )
                && operation.getLeaseToken() != null
                && operation.getLeaseExpiresAt() != null
                && operation.getLeaseExpiresAt().isAfter(Instant.now());
    }
}
