
package com.robotfleet.mission.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.robotfleet.mission.entity.MissionAssignment;

import jakarta.persistence.LockModeType;

public interface MissionAssignmentRepository
        extends JpaRepository<MissionAssignment, UUID> {

    Page<MissionAssignment> findByMissionId(
            UUID missionId,
            Pageable pageable
    );

    Page<MissionAssignment> findByRobotId(
            UUID robotId,
            Pageable pageable
    );

    Optional<MissionAssignment> findByReservationId(
            UUID reservationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM MissionAssignment a
            WHERE a.missionId = :missionId
              AND a.endedAt IS NULL
            """)
    Optional<MissionAssignment> findActiveByMissionIdForUpdate(
            @Param("missionId") UUID missionId
    );
}
