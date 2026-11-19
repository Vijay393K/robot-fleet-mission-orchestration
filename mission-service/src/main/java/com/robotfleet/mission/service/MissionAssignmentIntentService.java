
package com.robotfleet.mission.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.dto.MissionAssignmentRequest;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionRepository;

@Service
public class MissionAssignmentIntentService {

    private static final Set<AssignmentOperationStatus>
            BLOCKING_STATUSES = Set.of(
                    AssignmentOperationStatus.RESERVE_PENDING,
                    AssignmentOperationStatus.CONFIRM_PENDING,
                    AssignmentOperationStatus.CONFIRMED,
                    AssignmentOperationStatus.RELEASE_PENDING
            );

    private final MissionRepository missionRepository;
    private final MissionAssignmentOperationRepository operationRepository;

    public MissionAssignmentIntentService(
            MissionRepository missionRepository,
            MissionAssignmentOperationRepository operationRepository) {

        this.missionRepository = missionRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public UUID createAssignmentIntent(
            UUID missionId,
            MissionAssignmentRequest request,
            String correlationId) {

        if (missionId == null || request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mission ID and assignment request are required"
            );
        }

        if (request.robotId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot ID is required"
            );
        }

        if (request.actor() == null
                || request.actor().isBlank()
                || request.actor().length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid actor is required"
            );
        }

        if (request.reason() == null
                || request.reason().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Assignment reason is required"
            );
        }

        String normalizedCorrelationId =
                correlationId == null || correlationId.isBlank()
                        ? null
                        : correlationId.trim();

        if (normalizedCorrelationId != null
                && normalizedCorrelationId.length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Correlation ID must not exceed 100 characters"
            );
        }

        Mission mission = missionRepository
                .findByIdForUpdate(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found: " + missionId
                ));

        if (mission.getStatus() != MissionStatus.PENDING
                || mission.getAssignedRobotId() != null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an unassigned PENDING mission can be assigned"
            );
        }

        if (operationRepository.existsByMissionIdAndStatusIn(
                missionId,
                BLOCKING_STATUSES)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mission already has an active assignment operation"
            );
        }

        long nextGeneration;

        try {
            nextGeneration = Math.addExact(
                    mission.getAssignmentGeneration(),
                    1L
            );
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Assignment generation limit exceeded"
            );
        }

        MissionAssignmentOperation operation =
                new MissionAssignmentOperation(
                        missionId,
                        request.robotId(),
                        nextGeneration,
                        request.actor().trim(),
                        request.reason().trim(),
                        normalizedCorrelationId
                );

        mission.setAssignmentGeneration(nextGeneration);

        MissionAssignmentOperation savedOperation =
                operationRepository.saveAndFlush(operation);

        return savedOperation.getId();
    }
}
