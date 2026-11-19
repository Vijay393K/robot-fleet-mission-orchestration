
package com.robotfleet.mission.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class MissionRecoveryFinalizationService {

    public enum RecoveryFinalizationResult {
        FINALIZED,
        ALREADY_FINALIZED,
        NO_ASSIGNMENT_TO_CLOSE,
        NOT_RELEASED
    }

    private final MissionRepository missionRepository;
    private final MissionAssignmentRepository assignmentRepository;
    private final MissionAssignmentOperationRepository operationRepository;
    private final MissionEventRepository eventRepository;

    public MissionRecoveryFinalizationService(
            MissionRepository missionRepository,
            MissionAssignmentRepository assignmentRepository,
            MissionAssignmentOperationRepository operationRepository,
            MissionEventRepository eventRepository) {

        this.missionRepository = missionRepository;
        this.assignmentRepository = assignmentRepository;
        this.operationRepository = operationRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public RecoveryFinalizationResult finalizeReleasedAssignment(
            UUID operationId) {

        Objects.requireNonNull(operationId, "Operation ID is required");

        MissionAssignmentOperation snapshot = operationRepository
                .findById(operationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Assignment operation not found"
                ));

        Mission mission = missionRepository
                .findByIdForUpdate(snapshot.getMissionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Mission not found"
                ));

        MissionAssignmentOperation operation = operationRepository
                .findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Assignment operation not found"
                ));

        if (operation.getStatus() != AssignmentOperationStatus.RELEASED) {
            return RecoveryFinalizationResult.NOT_RELEASED;
        }

        if (!mission.getId().equals(operation.getMissionId())
                || operation.getReservationId() == null) {
            throw new IllegalStateException(
                    "Invalid assignment operation ownership"
            );
        }

        boolean currentAssignmentMatches =
                mission.getAssignmentGeneration()
                        == operation.getAssignmentGeneration()
                && Objects.equals(
                        mission.getAssignedRobotId(),
                        operation.getRobotId()
                );

        MissionAssignment assignment = assignmentRepository
                .findByReservationId(operation.getReservationId())
                .orElse(null);

        if (assignment == null) {
            boolean requiresAssignment =
                    mission.getStatus() == MissionStatus.RECOVERY_PENDING
                    || mission.getStatus() == MissionStatus.COMPLETED;

            if (requiresAssignment && currentAssignmentMatches) {
                throw new IllegalStateException(
                        "Assignment history missing for released reservation"
                );
            }

            return RecoveryFinalizationResult.NO_ASSIGNMENT_TO_CLOSE;
        }

        if (!assignment.getMissionId().equals(operation.getMissionId())
                || !assignment.getRobotId().equals(operation.getRobotId())
                || assignment.getAssignmentGeneration()
                        != operation.getAssignmentGeneration()) {
            throw new IllegalStateException(
                    "Assignment ownership mismatch"
            );
        }

        if (assignment.getEndedAt() != null) {
            if (mission.getStatus() == MissionStatus.RECOVERY_PENDING
                    && currentAssignmentMatches) {
                throw new IllegalStateException(
                        "Assignment closed while mission remains in recovery"
                );
            }

            return RecoveryFinalizationResult.ALREADY_FINALIZED;
        }

        MissionStatus status = mission.getStatus();

        if (status != MissionStatus.RECOVERY_PENDING
                && status != MissionStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Mission is not eligible for assignment finalization"
            );
        }

        if (!currentAssignmentMatches) {
            throw new IllegalStateException(
                    "Mission assignment generation or robot mismatch"
            );
        }

        MissionAssignment activeAssignment = assignmentRepository
                .findActiveByMissionIdForUpdate(mission.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Active assignment not found"
                ));

        if (!activeAssignment.getId().equals(assignment.getId())) {
            throw new IllegalStateException(
                    "Active assignment does not match released reservation"
            );
        }

        boolean recovery = status == MissionStatus.RECOVERY_PENDING;

        String endReason = recovery
                ? "Offline recovery: Fleet reservation released"
                : "Mission completed: Fleet reservation released";

        activeAssignment.close(endReason);
        assignmentRepository.saveAndFlush(activeAssignment);

        if (recovery) {
            mission.setAssignedRobotId(null);
            mission.setStatus(MissionStatus.PENDING);

            missionRepository.saveAndFlush(mission);

            MissionEvent event = new MissionEvent(
                    mission.getId(),
                    MissionStatus.RECOVERY_PENDING,
                    MissionStatus.PENDING,
                    mission.getAssignmentGeneration(),
                    "SYSTEM",
                    "Offline recovery completed after Fleet reservation release",
                    operation.getCorrelationId()
            );

            eventRepository.save(event);
        }

        return RecoveryFinalizationResult.FINALIZED;
    }
}
