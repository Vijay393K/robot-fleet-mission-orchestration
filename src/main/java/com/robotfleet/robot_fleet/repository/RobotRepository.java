
package com.robotfleet.robot_fleet.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotStatus;
import com.robotfleet.robot_fleet.RobotType;

public interface RobotRepository extends JpaRepository<Robot, UUID> {

    @Query("""
        SELECT r FROM Robot r
        WHERE (:type IS NULL OR r.type = :type)
          AND (:status IS NULL OR r.status = :status)
          AND (:minBattery IS NULL OR r.batteryPercentage >= :minBattery)
        """)
    Page<Robot> searchRobots(
            @Param("type") RobotType type,
            @Param("status") RobotStatus status,
            @Param("minBattery") Integer minBattery,
            Pageable pageable);

    List<Robot> findByLastHeartbeatBefore(Instant cutoff);

    List<Robot> findByLastHeartbeatIsNullOrLastHeartbeatBefore(
            Instant cutoff);

    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM Robot r
        WHERE r.type = :requiredRobotType
          AND r.status = :requiredStatus
          AND r.batteryPercentage >= :minimumBattery
          AND r.currentMissionId IS NULL
          AND r.lastHeartbeat >= :heartbeatCutoff
          AND r.lastHeartbeat <= :currentTime
        ORDER BY r.id
        """)
    List<Robot> findEligibleRobotsForScheduling(
            @Param("requiredRobotType") RobotType requiredRobotType,
            @Param("requiredStatus") RobotStatus requiredStatus,
            @Param("minimumBattery") int minimumBattery,
            @Param("heartbeatCutoff") Instant heartbeatCutoff,
            @Param("currentTime") Instant currentTime);
}
