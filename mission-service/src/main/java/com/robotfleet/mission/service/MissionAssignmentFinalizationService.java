
package com.robotfleet.mission.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignment;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.entity.MissionEvent;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionAssignmentRepository;
import com.robotfleet.mission.repository.MissionEventRepository;
import com.robotfleet.mission.repository.MissionRepository;
import com.robotfleet.mission.service.MissionAssignmentLeaseService.OperationClaim;

@Service
public class MissionAssignmentFinalizationService {

    public enum FinalizationResult {
        ASSIGNED,
        RELEASE_REQUIRED,
        STALE_CLAIM
    }

    private final MissionRepository missionRepository;
    private final MissionAssignmentRepository assignmentRepository;
    private final MissionEventRepository eventRepository;
    private final MissionAssignmentOperationRepository operationRepository;

    public MissionAssignmentFinalizationService(
            MissionRepository missionRepository,
            MissionAssignmentRepository assignmentRepository,
            MissionEventRepository eventRepository,
            MissionAssignmentOperationRepository operationRepository) {

        this.missionRepository = missionRepository;
        this.assignmentRepository = assignmentRepository;
        this.eventRepository = eventRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public FinalizationResult finalizeConfirmedReservation(
            OperationClaim claim,
            FleetRobotReservationResponse response) {

        Objects.requireNonNull(claim, "Operation claim is required");
        Objects.requireNonNull(response, "Fleet response is required");

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
            return FinalizationResult.STALE_CLAIM;
        }

        if (response.status()
                != FleetRobotReservationResponse.ReservationStatus.CONFIRMED) {

            throw new IllegalArgumentException(
                    "Fleet reservation must be CONFIRMED"
            );
        }

        if (!Objects.equals(
                    response.reservationId(),
                    operation.getReservationId()
                )
                || !Objects.equals(
                    response.robotId(),
                    operation.getRobotId()
                )
                || !Objects.equals(
                    response.missionId(),
                    operation.getMissionId()
                )) {

            operation.requestRelease();

            return FinalizationResult.RELEASE_REQUIRED;
        }

        Mission mission = missionRepository
                .findByIdForUpdate(operation.getMissionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Mission not found for assignment operation"
                ));

        boolean missionEligible =
                mission.getStatus() == MissionStatus.PENDING
                && mission.getAssignedRobotId() == null
                && mission.getAssignmentGeneration()
                        == operation.getAssignmentGeneration();

        if (!missionEligible) {

            operation.requestRelease();

            return FinalizationResult.RELEASE_REQUIRED;
        }

        MissionAssignment assignment = new MissionAssignment(
                operation.getMissionId(),
                operation.getRobotId(),
                operation.getReservationId(),
                operation.getAssignmentGeneration(),
                operation.getRequestedBy()
        );

        mission.setAssignedRobotId(operation.getRobotId());
        mission.setStatus(MissionStatus.ASSIGNED);

        missionRepository.saveAndFlush(mission);

        assignmentRepository.saveAndFlush(assignment);

        MissionEvent event = new MissionEvent(
                mission.getId(),
                MissionStatus.PENDING,
                MissionStatus.ASSIGNED,
                mission.getAssignmentGeneration(),
                operation.getRequestedBy(),
                "Robot assigned after Fleet reservation confirmation",
                operation.getCorrelationId()
        );

        eventRepository.save(event);

        operation.markConfirmed();

        return FinalizationResult.ASSIGNED;
    }

    private boolean ownsActiveLease(
            MissionAssignmentOperation operation,
            OperationClaim claim) {

        UUID currentToken = operation.getLeaseToken();

        Instant leaseExpiry = operation.getLeaseExpiresAt();

        return operation.getId().equals(claim.operationId())
                && operation.getMissionId().equals(claim.missionId())
                && operation.getRobotId().equals(claim.robotId())
                && operation.getAssignmentGeneration()
                        == claim.assignmentGeneration()
                && operation.getStatus()
                        == AssignmentOperationStatus.CONFIRM_PENDING
                && Objects.equals(
                        operation.getReservationId(),
                        claim.reservationId()
                )
                && currentToken != null
                && currentToken.equals(claim.leaseToken())
                && leaseExpiry != null
                && leaseExpiry.isAfter(Instant.now());
    }
}
