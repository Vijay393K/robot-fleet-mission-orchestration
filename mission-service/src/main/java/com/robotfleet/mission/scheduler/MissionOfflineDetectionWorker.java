
package com.robotfleet.mission.scheduler;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotStateResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.exception.FleetServiceException;
import com.robotfleet.mission.repository.MissionRepository;
import com.robotfleet.mission.service.MissionOfflineRecoveryService;
import com.robotfleet.mission.service.MissionOfflineRecoveryService.RecoveryResult;

@Component
public class MissionOfflineDetectionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(
                    MissionOfflineDetectionWorker.class
            );

    private static final int BATCH_SIZE = 20;

    private final MissionRepository missionRepository;
    private final FleetServiceClient fleetServiceClient;
    private final MissionOfflineRecoveryService recoveryService;

    private final AtomicInteger nextPage =
            new AtomicInteger(0);

    public MissionOfflineDetectionWorker(
            MissionRepository missionRepository,
            FleetServiceClient fleetServiceClient,
            MissionOfflineRecoveryService recoveryService) {

        this.missionRepository = missionRepository;
        this.fleetServiceClient = fleetServiceClient;
        this.recoveryService = recoveryService;
    }

    @Scheduled(
            initialDelayString =
                    "${mission.offline.initial-delay-ms:5000}",
            fixedDelayString =
                    "${mission.offline.delay-ms:10000}"
    )
    public void detectOfflineMissions() {

        try {
            Specification<Mission> activeMissions =
                    (root, query, cb) -> cb.and(
                            root.get("status").in(
                                    MissionStatus.ASSIGNED,
                                    MissionStatus.IN_PROGRESS
                            ),
                            cb.isNotNull(
                                    root.get("assignedRobotId")
                            )
                    );

            int pageNumber = nextPage.get();

            Page<Mission> page = missionRepository.findAll(
                    activeMissions,
                    PageRequest.of(
                            pageNumber,
                            BATCH_SIZE,
                            Sort.by("id").ascending()
                    )
            );

            if (page.isEmpty()) {
                nextPage.set(0);
                return;
            }

            // Rotate through pages across scheduled executions.
            nextPage.set(
                    page.hasNext() ? pageNumber + 1 : 0
            );

            for (Mission mission : page.getContent()) {

                try {
                    inspectMission(mission);

                } catch (FleetServiceException ex) {

                    log.warn(
                            "Fleet Service unavailable while checking "
                            + "missionId={}, robotId={}, failureType={}",
                            mission.getId(),
                            mission.getAssignedRobotId(),
                            ex.getFailureType()
                    );

                } catch (RuntimeException ex) {

                    log.error(
                            "Offline detection failed for missionId={}",
                            mission.getId(),
                            ex
                    );
                }
            }

        } catch (RuntimeException ex) {

            log.error(
                    "Offline mission detection cycle failed",
                    ex
            );
        }
    }

    private void inspectMission(Mission mission) {

        UUID missionId = mission.getId();
        UUID robotId = mission.getAssignedRobotId();

        if (robotId == null) {
            return;
        }

        // REST call occurs outside any Mission DB transaction.
        FleetRobotStateResponse robotState =
                fleetServiceClient.getRobotState(robotId);

        if (robotState == null
                || !robotId.equals(robotState.id())) {

            log.warn(
                    "Invalid Fleet robot state response. "
                    + "missionId={}, robotId={}",
                    missionId,
                    robotId
            );

            return;
        }

        // An unavailable REST service is not proof of
        // robot disconnection.
        if (!"OFFLINE".equals(robotState.status())) {
            return;
        }

        if (!missionId.equals(robotState.currentMissionId())) {

            log.warn(
                    "Fleet/Mission ownership mismatch. "
                    + "missionId={}, robotId={}, "
                    + "fleetCurrentMissionId={}",
                    missionId,
                    robotId,
                    robotState.currentMissionId()
            );
        }

        String correlationId =
                UUID.randomUUID().toString();

        RecoveryResult result =
                recoveryService.markRecoveryPending(
                        missionId,
                        robotState,
                        correlationId
                );

        if (result == RecoveryResult.RECOVERY_STARTED) {

            log.warn(
                    "Mission entered RECOVERY_PENDING. "
                    + "missionId={}, robotId={}, "
                    + "assignmentGeneration={}, "
                    + "correlationId={}",
                    missionId,
                    robotId,
                    mission.getAssignmentGeneration(),
                    correlationId
            );

        } else {

            log.debug(
                    "Offline recovery not applied. "
                    + "missionId={}, result={}",
                    missionId,
                    result
            );
        }
    }
}
