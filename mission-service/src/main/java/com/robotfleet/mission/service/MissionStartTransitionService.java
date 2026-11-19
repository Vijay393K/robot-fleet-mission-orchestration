
package com.robotfleet.mission.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.dto.FleetRobotStateResponse;
import com.robotfleet.mission.dto.MissionResponse;

import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignment;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.entity.MissionEvent;

import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.enums.MissionStatus;

import com.robotfleet.mission.repository.MissionRepository;
import com.robotfleet.mission.repository.MissionAssignmentRepository;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionEventRepository;

@Service
public class MissionStartTransitionService {

    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    private final MissionRepository missionRepository;
    private final MissionAssignmentRepository assignmentRepository;
    private final MissionAssignmentOperationRepository operationRepository;
    private final MissionEventRepository eventRepository;

    public MissionStartTransitionService(
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
    public MissionResponse startAssignedMission(
            UUID missionId,
            UUID robotId,
            long expectedGeneration,
            String actor,
            String reason,
            String correlationId,
            FleetRobotStateResponse fleetState) {

        validateRequest(
                missionId,
                robotId,
                expectedGeneration,
                actor,
                reason,
                correlationId
        );

        Mission mission = missionRepository
                .findByIdForUpdate(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found"
                ));

        if (!Objects.equals(
                mission.getAssignedRobotId(),
                robotId)
                || mission.getAssignmentGeneration()
                        != expectedGeneration) {

            throw conflict(
                    "Robot or assignment generation mismatch"
            );
        }

        if (mission.getStatus() != MissionStatus.ASSIGNED
                && mission.getStatus() != MissionStatus.IN_PROGRESS) {

            throw conflict(
                    "Only an ASSIGNED mission can be started"
            );
        }

        MissionAssignment assignment = assignmentRepository
                .findActiveByMissionIdForUpdate(missionId)
                .orElseThrow(() -> conflict(
                        "Active mission assignment not found"
                ));

        if (!assignment.getRobotId().equals(robotId)
                || assignment.getAssignmentGeneration()
                        != expectedGeneration) {

            throw conflict(
                    "Active assignment does not match the request"
            );
        }

        MissionAssignmentOperation operation = operationRepository
                .findByMissionIdAndAssignmentGeneration(
                        missionId,
                        expectedGeneration
                )
                .orElseThrow(() -> conflict(
                        "Assignment operation not found"
                ));

        if (operation.getStatus()
                        != AssignmentOperationStatus.CONFIRMED
                || !Objects.equals(
                        operation.getReservationId(),
                        assignment.getReservationId())
                || !operation.getRobotId().equals(robotId)) {

            throw conflict(
                    "Assignment reservation is not confirmed"
            );
        }

        // Idempotent replay: the same assignment has
        // already entered IN_PROGRESS.
        if (mission.getStatus() == MissionStatus.IN_PROGRESS) {
            return MissionResponse.from(mission);
        }

        // Fleet data must be obtained through REST before
        // calling this transactional method.
        validateFleetState(
                missionId,
                robotId,
                fleetState
        );

        mission.setStatus(MissionStatus.IN_PROGRESS);

        missionRepository.saveAndFlush(mission);

        MissionEvent event = new MissionEvent(
                missionId,
                MissionStatus.ASSIGNED,
                MissionStatus.IN_PROGRESS,
                expectedGeneration,
                actor.trim(),
                reason.trim(),
                normalizeCorrelationId(correlationId)
        );

        eventRepository.save(event);

        return MissionResponse.from(mission);
    }

    private void validateFleetState(
            UUID missionId,
            UUID robotId,
            FleetRobotStateResponse fleetState) {

        Instant now = Instant.now();

        if (fleetState == null
                || !robotId.equals(fleetState.id())
                || !missionId.equals(
                        fleetState.currentMissionId())
                || !"BUSY".equals(fleetState.status())
                || fleetState.safetyHold()) {

            throw conflict(
                    "Fleet robot is not ready to start this mission"
            );
        }

        Instant lastHeartbeat = fleetState.lastHeartbeat();

        if (lastHeartbeat == null
                || lastHeartbeat.isBefore(
                        now.minusSeconds(
                                HEARTBEAT_TIMEOUT_SECONDS))
                || lastHeartbeat.isAfter(
                        now.plusSeconds(5))) {

            throw conflict(
                    "Fleet robot heartbeat is invalid or stale"
            );
        }
    }

    private void validateRequest(
            UUID missionId,
            UUID robotId,
            long expectedGeneration,
            String actor,
            String reason,
            String correlationId) {

        if (missionId == null
                || robotId == null
                || expectedGeneration <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid mission ID, robot ID, "
                    + "and assignment generation are required"
            );
        }

        if (actor == null
                || actor.isBlank()
                || actor.length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid actor is required"
            );
        }

        if (reason == null || reason.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mission start reason is required"
            );
        }

        String normalizedCorrelationId =
                normalizeCorrelationId(correlationId);

        if (normalizedCorrelationId != null
                && normalizedCorrelationId.length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Correlation ID must not exceed 100 characters"
            );
        }
    }

    private String normalizeCorrelationId(
            String correlationId) {

        return correlationId == null
                || correlationId.isBlank()
                        ? null
                        : correlationId.trim();
    }

    private ResponseStatusException conflict(String message) {

        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
