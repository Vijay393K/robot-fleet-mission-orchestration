
package com.robotfleet.mission.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.robotfleet.mission.dto.MissionEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;



import com.robotfleet.mission.dto.MissionAssignmentResponse;
import com.robotfleet.mission.repository.MissionAssignmentRepository;

import com.robotfleet.mission.dto.MissionRequest;
import com.robotfleet.mission.dto.MissionResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionEvent;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.repository.MissionEventRepository;
import com.robotfleet.mission.repository.MissionRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionEventRepository missionEventRepository;
    private final MissionAssignmentRepository missionAssignmentRepository;
    
    public MissionService(
            MissionRepository missionRepository,
            MissionEventRepository missionEventRepository,
            MissionAssignmentRepository missionAssignmentRepository) {

        this.missionRepository = missionRepository;
        this.missionEventRepository = missionEventRepository;
        this.missionAssignmentRepository = missionAssignmentRepository;
    }

    @Transactional
    public MissionResponse createMission(MissionRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mission request is required"
            );
        }

        Mission mission = new Mission();

        mission.setMissionType(request.missionType());
        mission.setPriority(request.priority());
        mission.setRequiredRobotType(request.requiredRobotType());

        mission.setPickupX(request.pickupLocation().x());
        mission.setPickupY(request.pickupLocation().y());

        mission.setDropX(request.dropLocation().x());
        mission.setDropY(request.dropLocation().y());

        mission.setStatus(MissionStatus.PENDING);
        mission.setAssignmentGeneration(0L);

        Mission savedMission = missionRepository.saveAndFlush(mission);

        MissionEvent creationEvent = new MissionEvent(
                savedMission.getId(),
                null,
                MissionStatus.PENDING,
                savedMission.getAssignmentGeneration(),
                "SYSTEM",
                "Mission created",
                null
        );

        missionEventRepository.save(creationEvent);

        return MissionResponse.from(savedMission);
    }

    @Transactional(readOnly = true)
    public MissionResponse getMission(UUID missionId) {

        if (missionId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mission ID is required"
            );
        }
        

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mission not found: " + missionId
                ));

        return MissionResponse.from(mission);
    }

    @Transactional(readOnly = true)
    public Page<MissionResponse> getFilteredMissions(
            MissionStatus status,
            MissionPriority priority,
            MissionType missionType,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0 and size must be between 1 and 100"
            );
        }

        Specification<Mission> specification =
                (root, query, criteriaBuilder) -> {

                    List<Predicate> predicates = new ArrayList<>();

                    if (status != null) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("status"),
                                        status
                                )
                        );
                    }

                    if (priority != null) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("priority"),
                                        priority
                                )
                        );
                    }

                    if (missionType != null) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("missionType"),
                                        missionType
                                )
                        );
                    }

                    return criteriaBuilder.and(
                            predicates.toArray(new Predicate[0])
                    );
                };

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        return missionRepository
                .findAll(specification, pageable)
                .map(MissionResponse::from);
    }

@Transactional(readOnly = true)
public Page<MissionEventResponse> getMissionEvents(
        UUID missionId,
        int page,
        int size) {

    if (missionId == null) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Mission ID is required"
        );
    }

    if (page < 0 || size < 1 || size > 100) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Page must be >= 0 and size must be between 1 and 100"
        );
    }

    if (!missionRepository.existsById(missionId)) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Mission not found: " + missionId
        );
    }

    Pageable pageable = PageRequest.of(page, size);

    return missionEventRepository
            .findByMissionIdOrderByChangedAtAscIdAsc(
                    missionId,
                    pageable
            )
            .map(MissionEventResponse::from);
}

@Transactional(readOnly = true)
public Page<MissionAssignmentResponse> getMissionAssignments(
        UUID missionId,
        int page,
        int size) {

    if (missionId == null) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Mission ID is required"
        );
    }

    validatePagination(page, size);

    if (!missionRepository.existsById(missionId)) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Mission not found: " + missionId
        );
    }

    Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                    Sort.Order.desc("assignedAt"),
                    Sort.Order.desc("id")
            )
    );

    return missionAssignmentRepository
            .findByMissionId(missionId, pageable)
            .map(MissionAssignmentResponse::from);
}

@Transactional(readOnly = true)
public Page<MissionAssignmentResponse> getRobotAssignments(
        UUID robotId,
        int page,
        int size) {

    if (robotId == null) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Robot ID is required"
        );
    }

    validatePagination(page, size);

    Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                    Sort.Order.desc("assignedAt"),
                    Sort.Order.desc("id")
            )
    );

    return missionAssignmentRepository
            .findByRobotId(robotId, pageable)
            .map(MissionAssignmentResponse::from);
}

private void validatePagination(int page, int size) {

    if (page < 0 || size < 1 || size > 100) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Page must be >= 0 and size must be between 1 and 100"
        );
    }
}


}
