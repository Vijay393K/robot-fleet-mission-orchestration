
package com.robotfleet.mission.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;

@Service
public class MissionAssignmentLeaseService {

    private static final Duration LEASE_DURATION =
            Duration.ofSeconds(30);

    private final MissionAssignmentOperationRepository operationRepository;

    public MissionAssignmentLeaseService(
            MissionAssignmentOperationRepository operationRepository) {

        this.operationRepository = operationRepository;
    }

    @Transactional
    public List<OperationClaim> claimDueOperations(int batchSize) {

        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException(
                    "Batch size must be between 1 and 100"
            );
        }

        Instant now = Instant.now();

        List<UUID> operationIds =
                operationRepository.findDueOperationIdsForUpdate(
                        now,
                        batchSize
                );

        List<OperationClaim> claims = new ArrayList<>();

        for (UUID operationId : operationIds) {

            MissionAssignmentOperation operation =
                    operationRepository.findById(operationId)
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Locked operation not found: "
                                                    + operationId
                                    )
                            );

            UUID leaseToken = UUID.randomUUID();

            operation.claimLease(
                    leaseToken,
                    now.plus(LEASE_DURATION)
            );

            operation.recordAttempt(now);

            claims.add(new OperationClaim(
                    operation.getId(),
                    operation.getMissionId(),
                    operation.getRobotId(),
                    operation.getAssignmentGeneration(),
                    operation.getStatus(),
                    operation.getReservationId(),
                    operation.getReservationExpiresAt(),
                    leaseToken,
                    operation.getAttemptCount()
            ));
        }

        return List.copyOf(claims);
    }

    public record OperationClaim(
            UUID operationId,
            UUID missionId,
            UUID robotId,
            long assignmentGeneration,
            AssignmentOperationStatus status,
            UUID reservationId,
            Instant reservationExpiresAt,
            UUID leaseToken,
            long attemptCount
    ) {
    }
}
