package com.robotfleet.robot_fleet.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.MissionEvent;
import com.robotfleet.robot_fleet.MissionStatus;
import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotStatus;
import com.robotfleet.robot_fleet.dto.RobotReconciliationRequest;
import com.robotfleet.robot_fleet.repository.MissionEventRepository;
import com.robotfleet.robot_fleet.repository.MissionRepository;
import com.robotfleet.robot_fleet.repository.RobotRepository;

@Service
public class RobotReconciliationService {

    private final RobotRepository robotRepository;
    private final MissionRepository missionRepository;
    private final MissionEventRepository missionEventRepository;

    public RobotReconciliationService(
            RobotRepository robotRepository,
            MissionRepository missionRepository,
            MissionEventRepository missionEventRepository) {

        this.robotRepository = robotRepository;
        this.missionRepository = missionRepository;
        this.missionEventRepository = missionEventRepository;
    }

    @Transactional
    public Robot reconcileRobot(
            UUID robotId,
            RobotReconciliationRequest request) {

        if (request == null || !request.physicallySafe()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Operator must confirm that the robot is physically safe");
        }

        if (request.operator() == null || request.operator().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Operator name is required");
        }

        Robot robot = robotRepository.findByIdForReconciliation(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found"));

        if (!robot.isSafetyHold()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot is not under safety hold");
        }

        if (robot.getLastHeartbeat() == null
                || robot.getLastHeartbeat().isBefore(
                        Instant.now().minusSeconds(30))) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot heartbeat is not recent. Reconciliation is not allowed");
        }

        UUID missionId = robot.getCurrentMissionId();

        if (missionId != null) {

            Mission mission = missionRepository
                    .findByIdForRecovery(missionId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "The robot's current mission could not be found"));

            if (mission.getStatus() != MissionStatus.RECOVERY_PENDING) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The interrupted mission is not in RECOVERY_PENDING status");
            }

            if (mission.getAssignedRobot() == null
                    || !robotId.equals(mission.getAssignedRobot().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Mission assignment does not match this robot");
            }

            MissionStatus previousStatus = mission.getStatus();

            mission.setStatus(MissionStatus.PENDING);
            mission.setAssignedRobot(null);

            missionRepository.save(mission);

            MissionEvent event = new MissionEvent();
            event.setId(UUID.randomUUID());
            event.setMissionId(mission.getId());
            event.setPreviousStatus(previousStatus);
            event.setNewStatus(MissionStatus.PENDING);
            event.setChangedAt(Instant.now());
            event.setActor(request.operator());
            event.setReason(
                    "Robot safety reconciliation completed. Mission released for reassignment.");

            missionEventRepository.save(event);
        }

        robot.setCurrentMissionId(null);
        robot.setSafetyHold(false);
        robot.setStatus(RobotStatus.IDLE);

        return robotRepository.save(robot);
    }
}