
package com.robotfleet.robot_fleet.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.robotfleet.robot_fleet.entity.MissionAssignment;

public interface MissionAssignmentRepository
        extends JpaRepository<MissionAssignment, UUID> {

    List<MissionAssignment>
        findByMission_IdOrderByAssignedAtDesc(UUID missionId);

    List<MissionAssignment>
        findByRobot_IdOrderByAssignedAtDesc(UUID robotId);

    Optional<MissionAssignment>
        findByMission_IdAndEndedAtIsNull(UUID missionId);
}
