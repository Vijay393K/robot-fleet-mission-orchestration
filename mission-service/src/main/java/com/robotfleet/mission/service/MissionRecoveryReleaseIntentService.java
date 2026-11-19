
package com.robotfleet.mission.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignment;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.repository.MissionRepository;
import com.robotfleet.mission.repository.MissionAssignmentRepository;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;

@Service
public class MissionRecoveryReleaseIntentService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    MissionRecoveryReleaseIntentService.class
            );

    private final MissionRepository missionRepository;
    private final MissionAssignmentRepository assignmentRepository;
    private final MissionAssignmentOperationRepository operationRepository;

    public MissionRecoveryReleaseIntentService(
            MissionRepository missionRepository,
            MissionAssignmentRepository assignmentRepository,
            MissionAssignmentOperationRepository operationRepository) {

        this.missionRepository = missionRepository;
        this.assignmentRepository = assignmentRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public RecoveryReleaseIntent prepareRelease(
            UUID missionId,
            String operator,
            boolean physicallySafe,
            boolean executionStopped) {

        if (missionId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mission ID is required"
            );
        }

        if (operator == null
                || operator.isBlank()
                || operator.length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid operator is required"
            );
        }

        if (!physicallySafe || !executionStopped) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Physical safety and stopped execution "
                    + "must both be confirmed"
            );
        }

        Mission mission = missionRepository
                .findByIdForUpdate(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found"
                ));

        if (mission.getStatus()
                != MissionStatus.RECOVERY_PENDING) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mission is not awaiting recovery"
            );
        }

        MissionAssignment assignment =
                assignmentRepository
                        .findActiveByMissionIdForUpdate(missionId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "No active assignment found"
                                )
                        );

        if (!assignment.getRobotId().equals(
                    mission.getAssignedRobotId())
                || assignment.getAssignmentGeneration()
                        != mission.getAssignmentGeneration()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mission assignment state is inconsistent"
            );
        }

        MissionAssignmentOperation existingOperation =
                operationRepository
                        .findByMissionIdAndAssignmentGeneration(
                                missionId,
                                mission.getAssignmentGeneration()
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Assignment operation not found"
                                )
                        );

        MissionAssignmentOperation operation =
                operationRepository
                        .findByIdForUpdate(existingOperation.getId())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Assignment operation disappeared"
                                )
                        );

        if (!assignment.getReservationId().equals(
                    operation.getReservationId())
                || !assignment.getRobotId().equals(
                    operation.getRobotId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reservation ownership mismatch"
            );
        }

        if (operation.getStatus()
                == AssignmentOperationStatus.CONFIRMED) {

            operation.requestRelease();

            operationRepository.saveAndFlush(operation);

            log.warn(
                    "Recovery reservation release requested. "
                    + "missionId={}, robotId={}, "
                    + "reservationId={}, operator={}",
                    missionId,
                    assignment.getRobotId(),
                    assignment.getReservationId(),
                    operator.trim()
            );

        } else if (operation.getStatus()
                != AssignmentOperationStatus.RELEASE_PENDING) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reservation is not eligible for release preparation"
            );
        }

        return new RecoveryReleaseIntent(
                missionId,
                operation.getId(),
                operation.getReservationId(),
                operation.getStatus()
        );
    }

    public record RecoveryReleaseIntent(
            UUID missionId,
            UUID operationId,
            UUID reservationId,
            AssignmentOperationStatus status
    ) {
    }
}
