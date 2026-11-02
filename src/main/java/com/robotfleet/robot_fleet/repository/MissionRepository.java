
package com.robotfleet.robot_fleet.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.MissionStatus;

public interface MissionRepository extends JpaRepository<Mission, UUID>,
        JpaSpecificationExecutor<Mission> {

    List<Mission> findByAssignedRobot_Id(UUID robotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT m FROM Mission m
        WHERE m.status = :status
        ORDER BY m.createdAt ASC
        """)
    List<Mission> findPendingMissionsForScheduling(
            @Param("status") MissionStatus status);
}
