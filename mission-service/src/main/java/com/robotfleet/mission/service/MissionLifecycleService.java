
package com.robotfleet.mission.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.dto.MissionCancelRequest;
import com.robotfleet.mission.dto.MissionResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionEvent;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionEventRepository;
import com.robotfleet.mission.repository.MissionRepository;

@Service
public class MissionLifecycleService {

    private static final Set<AssignmentOperationStatus>
            BLOCKING_RESERVATION_STATUSES = Set.of(
                    AssignmentOperationStatus.RESERVE_PENDING,
                    AssignmentOperationStatus.CONFIRM_PENDING,
                    AssignmentOperationStatus.CONFIRMED,
                    AssignmentOperationStatus.RELEASE_PENDING
            );

    private final MissionRepository missionRepository;
    private final MissionEventRepository missionEventRepository;
    private final MissionAssignmentOperationRepository operationRepository;

    public MissionLifecycleService(
            MissionRepository missionRepository,
            MissionEventRepository missionEventRepository,
            MissionAssignmentOperationRepository operationRepository) {

        this.missionRepository = missionRepository;
        this.missionEventRepository = missionEventRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional
    public MissionResponse cancelPendingMission(
            UUID missionId,
            MissionCancelRequest request,
            String correlationId) {

        if (missionId == null || request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mission ID and cancellation request are required"
            );
        }

        if (request.actor() == null
                || request.actor().isBlank()
                || request.actor().length() > 100
                || request.reason() == null
                || request.reason().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid actor and cancellation reason are required"
            );
        }

        if (correlationId != null
                && correlationId.length() > 100) {

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
                    "Only an unassigned PENDING mission can be cancelled"
            );
        }

        if (operationRepository.existsByMissionIdAndStatusIn(
                missionId,
                BLOCKING_RESERVATION_STATUSES)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mission has an active or unresolved reservation operation"
            );
        }

        MissionStatus previousStatus = mission.getStatus();

        mission.setStatus(MissionStatus.CANCELLED);

        missionRepository.saveAndFlush(mission);

        MissionEvent cancellationEvent = new MissionEvent(
                missionId,
                previousStatus,
                MissionStatus.CANCELLED,
                mission.getAssignmentGeneration(),
                request.actor().trim(),
                request.reason().trim(),
                correlationId
        );

        missionEventRepository.save(cancellationEvent);

        return MissionResponse.from(mission);
    }
}
