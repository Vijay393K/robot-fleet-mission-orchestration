
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
public class MissionCompletionTransitionService {

    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    private final MissionRepository missionRepository;
    private final MissionAssignmentRepository assignmentRepository;
    private final MissionAssignmentOperationRepository operationRepository;
    private final MissionEventRepository eventRepository;

    public MissionCompletionTransitionService(
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
    public MissionResponse completeMission(
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

        // Successful replay must not create duplicate events.
        if (mission.getStatus() == MissionStatus.COMPLETED) {
            return MissionResponse.from(mission);
        }

        if (mission.getStatus() != MissionStatus.IN_PROGRESS) {
            throw conflict(
                    "Only an IN_PROGRESS mission can be completed"
            );
        }

        MissionAssignment assignment = assignmentRepository
                .findActiveByMissionIdForUpdate(missionId)
                .orElseThrow(() -> conflict(
                        "Active assignment not found"
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
                    "Confirmed reservation does not match assignment"
            );
        }

        validateFleetState(missionId, robotId, fleetState);

        // Mission completion and the durable release intent
        // are committed in the same local database transaction.
        mission.setStatus(MissionStatus.COMPLETED);

        operation.requestRelease();

        MissionEvent event = new MissionEvent(
                missionId,
                MissionStatus.IN_PROGRESS,
                MissionStatus.COMPLETED,
                expectedGeneration,
                actor.trim(),
                reason.trim(),
                normalizeCorrelationId(correlationId)
        );

        missionRepository.saveAndFlush(mission);
        operationRepository.saveAndFlush(operation);
        eventRepository.save(event);

        // Do not close assignment history here.
        // The reservation release must be confirmed first.
        return MissionResponse.from(mission);
    }

    private void validateFleetState(
            UUID missionId,
            UUID robotId,
            FleetRobotStateResponse state) {

        Instant now = Instant.now();

        if (state == null
                || !robotId.equals(state.id())
                || !missionId.equals(state.currentMissionId())
                || !"BUSY".equals(state.status())
                || state.safetyHold()) {

            throw conflict(
                    "Fleet robot is not eligible to complete this mission"
            );
        }

        Instant heartbeat = state.lastHeartbeat();

        if (heartbeat == null
                || heartbeat.isBefore(
                        now.minusSeconds(HEARTBEAT_TIMEOUT_SECONDS))
                || heartbeat.isAfter(now.plusSeconds(5))) {

            throw conflict(
                    "Fleet robot heartbeat is invalid or stale"
            );
        }
    }

    private void validateRequest(
            UUID missionId,
            UUID robotId,
            long generation,
            String actor,
            String reason,
            String correlationId) {

        if (missionId == null
                || robotId == null
                || generation <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid mission ID, robot ID and generation are required"
            );
        }

        if (actor == null
                || actor.isBlank()
                || actor.length() > 100
                || reason == null
                || reason.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid actor and reason are required"
            );
        }

        String normalized = normalizeCorrelationId(correlationId);

        if (normalized != null && normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Correlation ID must not exceed 100 characters"
            );
        }
    }

    private String normalizeCorrelationId(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
