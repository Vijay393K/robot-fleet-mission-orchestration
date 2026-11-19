
package com.robotfleet.fleet.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.enums.RobotType;

import jakarta.persistence.LockModeType;

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
        WHERE r.lastHeartbeat < :cutoff
          AND r.status IN (
              com.robotfleet.fleet.enums.RobotStatus.IDLE,
              com.robotfleet.fleet.enums.RobotStatus.BUSY,
              com.robotfleet.fleet.enums.RobotStatus.CHARGING
          )
        ORDER BY r.id
        """)
    List<Robot> findStaleRobotsForRecovery(
            @Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Robot r WHERE r.id = :robotId")
    Optional<Robot> findByIdForUpdate(
            @Param("robotId") UUID robotId);
}
