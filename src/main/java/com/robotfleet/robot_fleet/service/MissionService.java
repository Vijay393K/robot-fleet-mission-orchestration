package com.robotfleet.robot_fleet.service;

import java.util.UUID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.MissionEvent;
import com.robotfleet.robot_fleet.MissionStatus;
import com.robotfleet.robot_fleet.MissionPriority;
import com.robotfleet.robot_fleet.MissionType;
import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotStatus;

import com.robotfleet.robot_fleet.dto.MissionRequest;

import com.robotfleet.robot_fleet.repository.MissionRepository;
import com.robotfleet.robot_fleet.repository.MissionEventRepository;
import com.robotfleet.robot_fleet.repository.RobotRepository;

@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionEventRepository missionEventRepository;
    private final RobotRepository robotRepository;

    public MissionService(
            MissionRepository missionRepository,
            MissionEventRepository missionEventRepository,
            RobotRepository robotRepository) {

        this.missionRepository = missionRepository;
        this.missionEventRepository = missionEventRepository;
        this.robotRepository = robotRepository;
    }

    @Transactional
    public Mission createMission(MissionRequest request) {

        Mission mission = new Mission();

        mission.setId(UUID.randomUUID());

        mission.setMissionType(request.getMissionType());
        mission.setPriority(request.getPriority());
        mission.setRequiredRobotType(request.getRequiredRobotType());

        mission.setPickupX(request.getPickupLocation().getX());
        mission.setPickupY(request.getPickupLocation().getY());

        mission.setDropX(request.getDropLocation().getX());
        mission.setDropY(request.getDropLocation().getY());

        mission.setStatus(MissionStatus.PENDING);

        return missionRepository.save(mission);
    }

    @Transactional(readOnly = true)
    public Mission getMission(UUID missionId) {

        return missionRepository.findById(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found with ID: " + missionId
                ));
    }

    @Transactional(readOnly = true)
    public List<Mission> getAllMissions() {
        return missionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Mission> getFilteredMissions(
            MissionStatus status,
            MissionPriority priority,
            MissionType missionType,
            int page,
            int size) {

        Specification<Mission> specification = (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(
                        criteriaBuilder.equal(root.get("status"), status)
                );
            }

            if (priority != null) {
                predicates.add(
                        criteriaBuilder.equal(root.get("priority"), priority)
                );
            }

            if (missionType != null) {
                predicates.add(
                        criteriaBuilder.equal(root.get("missionType"), missionType)
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0])
            );
        };

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return missionRepository.findAll(specification, pageable);
    }

    private void recordMissionEvent(
            Mission mission,
            MissionStatus previousStatus,
            MissionStatus newStatus,
            String actor,
            String reason) {

        MissionEvent event = new MissionEvent();

        event.setId(UUID.randomUUID());
        event.setMissionId(mission.getId());
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(newStatus);
        event.setChangedAt(Instant.now());
        event.setActor(actor);
        event.setReason(reason);

        missionEventRepository.save(event);
    }

    @Transactional
    public Mission changeMissionStatus(
            UUID missionId,
            MissionStatus newStatus,
            String actor,
            String reason) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found with ID: " + missionId
                ));

        MissionStatus previousStatus = mission.getStatus();

        boolean validTransition = switch (previousStatus) {

            case PENDING ->
                    newStatus == MissionStatus.CANCELLED;
            case ASSIGNED ->
                    newStatus == MissionStatus.IN_PROGRESS ||
                    newStatus == MissionStatus.FAILED ||
                    newStatus == MissionStatus.CANCELLED;

            case IN_PROGRESS ->
                    newStatus == MissionStatus.COMPLETED ||
                    newStatus == MissionStatus.FAILED;

            case RECOVERY_PENDING,
                 COMPLETED,
                 FAILED,
                 CANCELLED -> false;
        };

        if (!validTransition) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid mission status transition from "
                            + previousStatus + " to " + newStatus
            );
        }

        mission.setStatus(newStatus);

        Mission savedMission = missionRepository.save(mission);

        recordMissionEvent(
                savedMission,
                previousStatus,
                newStatus,
                actor,
                reason
        );

        return savedMission;
    }

    @Transactional(readOnly = true)
    public List<MissionEvent> getMissionEvents(UUID missionId) {

        if (!missionRepository.existsById(missionId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Mission not found with ID: " + missionId
            );
        }

        return missionEventRepository
                .findByMissionIdOrderByChangedAtAsc(missionId);
    }

    @Transactional
    public Mission assignMission(
            UUID missionId,
            UUID robotId,
            String actor,
            String reason) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found"
                ));

        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Robot not found"
                ));

        if (mission.getStatus() != MissionStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only pending missions can be assigned"
            );
        }

        if (robot.getType() != mission.getRequiredRobotType()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot type does not match mission requirement"
            );
        }

        if (robot.getStatus() != RobotStatus.IDLE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot is not idle"
            );
        }

        if (robot.isSafetyHold()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot is under safety hold and cannot receive missions"
            );
        }

        if (robot.getBatteryPercentage() < 20) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot battery must be at least 20 percent"
            );
        }

        if (robot.getCurrentMissionId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot is already assigned to another mission"
            );
        }

        if (robot.getLastHeartbeat() == null ||
                robot.getLastHeartbeat().isBefore(Instant.now().minusSeconds(30))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot heartbeat is missing or outdated"
            );
        }

        MissionStatus previousStatus = mission.getStatus();

        mission.setAssignmentGeneration(
                mission.getAssignmentGeneration() + 1
        );

        mission.setAssignedRobot(robot);
        mission.setStatus(MissionStatus.ASSIGNED);

        robot.setStatus(RobotStatus.BUSY);
        robot.setCurrentMissionId(mission.getId());

        robotRepository.save(robot);

        Mission savedMission = missionRepository.save(mission);

        recordMissionEvent(
                savedMission,
                previousStatus,
                MissionStatus.ASSIGNED,
                actor,
                reason
        );

        return savedMission;
    }

    @Transactional
    public Mission startMission(
            UUID missionId,
            UUID robotId,
            String actor,
            String reason) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found"
                ));

        if (mission.getStatus() != MissionStatus.ASSIGNED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only assigned missions can be started"
            );
        }

        Robot robot = mission.getAssignedRobot();

        if (robot == null || !robot.getId().equals(robotId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This robot is not assigned to this mission"
            );
        }

        if (robot.getStatus() != RobotStatus.BUSY ||
                !missionId.equals(robot.getCurrentMissionId())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot is not currently assigned to this mission"
            );
        }

        MissionStatus previousStatus = mission.getStatus();

        mission.setStatus(MissionStatus.IN_PROGRESS);

        Mission savedMission = missionRepository.save(mission);

        recordMissionEvent(
                savedMission,
                previousStatus,
                MissionStatus.IN_PROGRESS,
                actor,
                reason
        );

        return savedMission;
    }

    @Transactional
    public Mission completeMission(
            UUID missionId,
            UUID robotId,
            long assignmentGeneration,
            String actor,
            String reason) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found"
                ));

        if (mission.getStatus() != MissionStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only in-progress missions can be completed"
            );
        }

        if (mission.getAssignmentGeneration() != assignmentGeneration) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stale assignment generation. This mission has been reassigned."
            );
        }

        Robot robot = mission.getAssignedRobot();

        if (robot == null || !robot.getId().equals(robotId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This robot is no longer assigned to this mission"
            );
        }

        if (!missionId.equals(robot.getCurrentMissionId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot is not currently executing this mission"
            );
        }

        if (robot.getStatus() != RobotStatus.BUSY || robot.isSafetyHold()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Robot cannot complete the mission while offline or under safety hold"
            );
        }

        MissionStatus previousStatus = mission.getStatus();

        mission.setStatus(MissionStatus.COMPLETED);

        robot.setStatus(RobotStatus.IDLE);
        robot.setCurrentMissionId(null);

        robotRepository.save(robot);

        Mission savedMission = missionRepository.save(mission);

        recordMissionEvent(
                savedMission,
                previousStatus,
                MissionStatus.COMPLETED,
                actor,
                reason
        );

        return savedMission;
    }

    @Transactional(readOnly = true)
    public List<Mission> getMissionsByRobot(UUID robotId) {

        if (!robotRepository.existsById(robotId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Robot not found: " + robotId
            );
        }

        return missionRepository.findByAssignedRobot_Id(robotId);
    }
}