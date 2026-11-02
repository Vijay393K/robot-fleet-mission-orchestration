
package com.robotfleet.robot_fleet.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.MissionEvent;
import com.robotfleet.robot_fleet.MissionPriority;
import com.robotfleet.robot_fleet.MissionStatus;
import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotStatus;
import com.robotfleet.robot_fleet.repository.MissionEventRepository;
import com.robotfleet.robot_fleet.repository.MissionRepository;
import com.robotfleet.robot_fleet.repository.RobotRepository;

@Service
public class MissionSchedulerService {

    private static final int MINIMUM_BATTERY = 25;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    private final MissionRepository missionRepository;
    private final RobotRepository robotRepository;
    private final MissionEventRepository missionEventRepository;

    public MissionSchedulerService(
            MissionRepository missionRepository,
            RobotRepository robotRepository,
            MissionEventRepository missionEventRepository) {

        this.missionRepository = missionRepository;
        this.robotRepository = robotRepository;
        this.missionEventRepository = missionEventRepository;
    }

    @Transactional
    public List<Mission> executeScheduling() {

        Instant now = Instant.now();
        Instant heartbeatCutoff =
                now.minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);

        List<Mission> pendingMissions =
                missionRepository.findPendingMissionsForScheduling(
                        MissionStatus.PENDING);

        pendingMissions.sort(
                Comparator
                    .comparingInt((Mission mission) ->
                        SchedulingUtils.getPriorityRank(
                                mission.getPriority().name()))
                    .thenComparing(Mission::getCreatedAt)
                    .thenComparing(Mission::getId)
        );

        List<Mission> assignedMissions = new ArrayList<>();

        for (Mission mission : pendingMissions) {

            List<Robot> eligibleRobots =
                    robotRepository.findEligibleRobotsForScheduling(
                            mission.getRequiredRobotType(),
                            RobotStatus.IDLE,
                            MINIMUM_BATTERY,
                            heartbeatCutoff,
                            now
                    );

            if (eligibleRobots.isEmpty()) {
                continue;
            }

            Robot selectedRobot = eligibleRobots.stream()
                    .min(Comparator.comparingDouble(robot ->
                            SchedulingUtils.calculateDistance(
                                    robot.getLocationX(),
                                    robot.getLocationY(),
                                    mission.getPickupX(),
                                    mission.getPickupY()
                            )))
                    .orElseThrow();

            MissionStatus previousStatus = mission.getStatus();

            mission.setAssignedRobot(selectedRobot);
            mission.setStatus(MissionStatus.ASSIGNED);

            selectedRobot.setStatus(RobotStatus.BUSY);
            selectedRobot.setCurrentMissionId(mission.getId());

            robotRepository.save(selectedRobot);
            Mission savedMission = missionRepository.save(mission);

            recordMissionEvent(
                    savedMission,
                    previousStatus,
                    MissionStatus.ASSIGNED,
                    "SCHEDULER",
                    "Automatically assigned to the closest eligible robot"
            );

            assignedMissions.add(savedMission);
        }

        return assignedMissions;
    }

    private void recordMissionEvent(
            Mission mission,
            MissionStatus previousStatus,
            MissionStatus newStatus,
            String actor,
            String reason) {

        MissionEvent event = new MissionEvent();

        event.setId(java.util.UUID.randomUUID());
        event.setMissionId(mission.getId());
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(newStatus);
        event.setChangedAt(Instant.now());
        event.setActor(actor);
        event.setReason(reason);

        missionEventRepository.save(event);
    }
}
