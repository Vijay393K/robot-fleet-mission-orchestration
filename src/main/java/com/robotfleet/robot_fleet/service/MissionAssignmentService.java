package com.robotfleet.robot_fleet.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.robot_fleet.dto.MissionAssignmentResponse;
import com.robotfleet.robot_fleet.repository.MissionAssignmentRepository;
import com.robotfleet.robot_fleet.repository.MissionRepository;
import com.robotfleet.robot_fleet.repository.RobotRepository;

@Service
public class MissionAssignmentService {

    private final MissionAssignmentRepository assignmentRepository;
    private final MissionRepository missionRepository;
    private final RobotRepository robotRepository;

    public MissionAssignmentService(
            MissionAssignmentRepository assignmentRepository,
            MissionRepository missionRepository,
            RobotRepository robotRepository) {

        this.assignmentRepository = assignmentRepository;
        this.missionRepository = missionRepository;
        this.robotRepository = robotRepository;
    }

    @Transactional(readOnly = true)
    public List<MissionAssignmentResponse> getMissionAssignmentHistory(
            UUID missionId) {

        if (!missionRepository.existsById(missionId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Mission not found");
        }

        return assignmentRepository
                .findByMission_IdOrderByAssignedAtDesc(missionId)
                .stream()
                .map(MissionAssignmentResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MissionAssignmentResponse> getRobotAssignmentHistory(
            UUID robotId) {

        if (!robotRepository.existsById(robotId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Robot not found");
        }

        return assignmentRepository
                .findByRobot_IdOrderByAssignedAtDesc(robotId)
                .stream()
                .map(MissionAssignmentResponse::new)
                .toList();
    }
}
