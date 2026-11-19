
package com.robotfleet.mission.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.dto.FleetRobotStateResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionEvent;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.repository.MissionEventRepository;
import com.robotfleet.mission.repository.MissionRepository;

@Service
public class MissionOfflineRecoveryService {

    public enum RecoveryResult {
        RECOVERY_STARTED,
        ALREADY_RECOVERING,
        ROBOT_NOT_OFFLINE,
        ROBOT_MISMATCH,
        MISSION_NOT_ACTIVE
    }

    private final MissionRepository missionRepository;
    private final MissionEventRepository eventRepository;

    public MissionOfflineRecoveryService(
            MissionRepository missionRepository,
            MissionEventRepository eventRepository) {

        this.missionRepository = missionRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public RecoveryResult markRecoveryPending(
            UUID missionId,
            FleetRobotStateResponse robotState,
            String correlationId) {

        Objects.requireNonNull(
                missionId,
                "Mission ID is required"
        );

        Objects.requireNonNull(
                robotState,
                "Fleet robot state is required"
        );

        if (robotState.id() == null) {
            throw new IllegalArgumentException(
                    "Fleet robot response is missing robot ID"
            );
        }

        String normalizedCorrelationId =
                correlationId == null || correlationId.isBlank()
                        ? null
                        : correlationId.trim();

        if (normalizedCorrelationId != null
                && normalizedCorrelationId.length() > 100) {

            throw new IllegalArgumentException(
                    "Correlation ID exceeds 100 characters"
            );
        }

        // The robot must be explicitly reported OFFLINE.
        // A REST failure alone is not proof of disconnection.
        if (!"OFFLINE".equals(robotState.status())) {
            return RecoveryResult.ROBOT_NOT_OFFLINE;
        }

        // Lock the mission before checking its current state.
        Mission mission = missionRepository
                .findByIdForUpdate(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found: " + missionId
                ));

        // Do not change a mission assigned to another robot.
        if (!robotState.id().equals(
                mission.getAssignedRobotId())) {

            return RecoveryResult.ROBOT_MISMATCH;
        }

        // Repeated detection must not create duplicate events.
        if (mission.getStatus()
                == MissionStatus.RECOVERY_PENDING) {

            return RecoveryResult.ALREADY_RECOVERING;
        }

        MissionStatus previousStatus = mission.getStatus();

        // Only missions currently executing or assigned
        // can enter offline recovery.
        if (previousStatus != MissionStatus.ASSIGNED
                && previousStatus != MissionStatus.IN_PROGRESS) {

            return RecoveryResult.MISSION_NOT_ACTIVE;
        }

        // Preserve robot ownership and assignment generation.
        // The existing assignment history remains active.
        mission.setStatus(MissionStatus.RECOVERY_PENDING);

        missionRepository.saveAndFlush(mission);

        String reason =
                "Assigned robot reported OFFLINE by Fleet Service"
                + "; robotId=" + robotState.id()
                + "; safetyHold=" + robotState.safetyHold()
                + "; fleetCurrentMissionId="
                + robotState.currentMissionId();

        MissionEvent event = new MissionEvent(
                mission.getId(),
                previousStatus,
                MissionStatus.RECOVERY_PENDING,
                mission.getAssignmentGeneration(),
                "SYSTEM",
                reason,
                normalizedCorrelationId
        );

        eventRepository.save(event);

        return RecoveryResult.RECOVERY_STARTED;
    }
}
