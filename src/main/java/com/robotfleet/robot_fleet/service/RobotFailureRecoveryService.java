package com.robotfleet.robot_fleet.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.MissionEvent;
import com.robotfleet.robot_fleet.MissionStatus;
import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotStatus;

import com.robotfleet.robot_fleet.repository.MissionEventRepository;
import com.robotfleet.robot_fleet.repository.MissionRepository;
import com.robotfleet.robot_fleet.repository.RobotRepository;

@Service
public class RobotFailureRecoveryService {

    private final RobotRepository robotRepository;
    private final MissionRepository missionRepository;
    private final MissionEventRepository missionEventRepository;

    public RobotFailureRecoveryService(
            RobotRepository robotRepository,
            MissionRepository missionRepository,
            MissionEventRepository missionEventRepository) {

        this.robotRepository = robotRepository;
        this.missionRepository = missionRepository;
        this.missionEventRepository = missionEventRepository;
    }

    @Transactional
    public void detectAndRecoverOfflineRobots() {

        Instant cutoff = Instant.now().minusSeconds(30);

        List<Robot> staleRobots =
                robotRepository.findStaleRobotsForRecovery(cutoff);

        for (Robot robot : staleRobots) {

            UUID robotId = robot.getId();

            robot.setStatus(RobotStatus.OFFLINE);
            robot.setSafetyHold(true);

            UUID missionId = robot.getCurrentMissionId();

            if (missionId != null) {

                Mission mission = missionRepository
                        .findByIdForRecovery(missionId)
                        .orElse(null);

                if (mission != null
                        && mission.getAssignedRobot() != null
                        && robotId.equals(
                                mission.getAssignedRobot().getId())
                        && (mission.getStatus() == MissionStatus.ASSIGNED
                        || mission.getStatus() == MissionStatus.IN_PROGRESS)) {

                    MissionStatus previousStatus = mission.getStatus();

                    mission.setStatus(MissionStatus.RECOVERY_PENDING);

                    missionRepository.save(mission);

                    recordDisconnectionEvent(
                            mission,
                            previousStatus,
                            robotId
                    );
                }
            }

            robotRepository.save(robot);

            System.out.println(
                    "Robot marked OFFLINE: " + robotId);
        }
    }

    private void recordDisconnectionEvent(
            Mission mission,
            MissionStatus previousStatus,
            UUID robotId) {

        MissionEvent event = new MissionEvent();

        event.setId(UUID.randomUUID());
        event.setMissionId(mission.getId());
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(MissionStatus.RECOVERY_PENDING);
        event.setChangedAt(Instant.now());
        event.setActor("SYSTEM");
        event.setReason(
                "Robot heartbeat timeout. Disconnected robot: " + robotId);

        missionEventRepository.save(event);
    }
}